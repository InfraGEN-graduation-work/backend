package com.infragen.infragen.domain.project.service.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.repository.projection.ProjectSuccessionCandidatePreview;
import com.infragen.infragen.domain.project.service.command.ProjectOwnershipSuccessionPlan.Deletion;
import com.infragen.infragen.domain.project.service.command.ProjectOwnershipSuccessionPlan.Transfer;

import lombok.RequiredArgsConstructor;

/**
 * 회원이 떠날 때 그 회원이 소유한 프로젝트를 다른 참여자에게 승계하거나, 후보가 없으면 삭제한다.
 * 승계 대상 선정(prepare)과 실제 변경(execute)을 나눠, 호출자가 두 단계 사이에 필요한 회원을
 * member ID 순으로 한꺼번에 잠글 수 있게 한다.
 * 두 단계 모두 호출자의 transaction에 참여하며, 회원 비활성화·다른 프로젝트 참여 정리·초대 취소는 호출자가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class ProjectOwnershipSuccessionCommandService {
    private static final Comparator<ProjectSuccessionCandidatePreview> REGULAR_MEMBER_PRIORITY = Comparator
            .comparingInt((ProjectSuccessionCandidatePreview candidate) ->
                    candidate.getCollaboratorRole() == ProjectCollaboratorRole.EDITOR ? 0 : 1)
            .thenComparing(ProjectSuccessionCandidatePreview::getJoinedAt)
            .thenComparing(ProjectSuccessionCandidatePreview::getMembershipId);

    private final ProjectRepository projectRepository;
    private final ProjectCollaboratorRepository collaboratorRepository;
    private final MemberRepository memberRepository;
    private final ProjectCommandService projectCommandService;

    /**
     * 호출자가 이미 잠근 프로젝트 중 떠나는 회원이 현재 owner인 것마다 승계 또는 삭제 계획을 만든다.
     * 일반 회원 후보를 우선해 EDITOR→VIEWER, 참여 시각, membership ID 순으로 고르고,
     * 일반 회원이 없으면 활성 guest 중 무작위로 고른다. guest 무작위 선택은 여기서 한 번만 한다.
     * 회원을 잠그거나 owner·membership·초대를 변경하지 않는다.
     *
     * @param lockedProjectIds 호출자가 같은 transaction에서 이미 쓰기 잠금한 project ID 목록
     * @return 입력 순서를 따르는 변경 불가 계획 목록. 이미 삭제됐거나 owner가 바뀐 프로젝트는 제외한다
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ProjectOwnershipSuccessionPlan> prepareSuccessionPlans(
            Long departingMemberId, List<Long> lockedProjectIds
    ) {
        List<ProjectOwnershipSuccessionPlan> plans = new ArrayList<>();

        for (Long projectId : lockedProjectIds) {
            Optional<Project> project = projectRepository.findByIdForUpdate(projectId);
            if (project.isEmpty() || !project.get().getMember().getId().equals(departingMemberId)) {
                continue;
            }

            // 후보가 있으면 승계(Transfer), 없으면 삭제(Deletion) 계획을 넣는다.
            // 명시한 타입 인자가 없으면 map 결과가 Optional<Transfer>로 추론돼 Deletion을 반환할 수 없다.
            plans.add(selectSuccessor(projectId, departingMemberId)
                    .<ProjectOwnershipSuccessionPlan>map(successor -> new Transfer(projectId, departingMemberId,
                            successor.getMemberId(), successor.getMemberRole()))
                    .orElseGet(() -> new Deletion(projectId, departingMemberId)));
        }

        return List.copyOf(plans);
    }

    /**
     * prepare에서 만든 계획대로 프로젝트를 승계하거나 삭제한다.
     * 호출자는 계획의 프로젝트와 떠나는 회원·승계 회원을 모두 잠근 뒤 호출해야 한다.
     * 승계에서는 새 owner의 참여 기록을 지우고 owner만 바꾸며, 떠나는 회원을 참여자로 남기지 않는다.
     * 계획과 현재 상태가 다르면 다른 후보로 바꾸거나 삭제로 대체하지 않고 예외로 전체 처리를 중단한다.
     *
     * @throws ProjectException 프로젝트가 없거나 owner가 바뀐 경우 PROJECT_NOT_FOUND,
     *                          승계 회원이 비활성·유형 변경·참여 기록 부재인 경우 OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void executeSuccessionPlans(List<ProjectOwnershipSuccessionPlan> plans) {
        for (ProjectOwnershipSuccessionPlan plan : plans) {
            switch (plan) {
                case Transfer transfer -> transfer(transfer);
                // 삭제 경로가 project 잠금·owner 확인·종속 데이터 정리를 같은 transaction에서 다시 수행한다.
                case Deletion deletion ->
                        projectCommandService.deleteProject(deletion.projectId(), deletion.departingMemberId());
            }
        }
    }

    private void transfer(Transfer plan) {
        // 앞선 계획의 bulk 삭제가 캐시를 비웠을 수 있으므로 계획의 ID로 다시 조회한다.
        Project project = projectRepository.findByIdForUpdate(plan.projectId())
                .filter(found -> found.getMember().getId().equals(plan.departingMemberId()))
                .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));
        Member newOwner = memberRepository.findByIdForUpdate(plan.successorMemberId())
                .filter(member -> Boolean.TRUE.equals(member.getIsActive()))
                .filter(member -> member.getRole() == plan.successorRole())
                .orElseThrow(() -> new ProjectException(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE));
        if (collaboratorRepository.deleteByProjectIdAndMemberId(plan.projectId(), newOwner.getId()) != 1L) {
            throw new ProjectException(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE);
        }
        project.transferOwnershipTo(newOwner);
    }

    // 승계 대상 후보를 고른다. 일반 회원이 있으면 우선하고, 없으면 활성 guest 중 무작위로 고른다.
    private Optional<ProjectSuccessionCandidatePreview> selectSuccessor(Long projectId, Long departingMemberId) {
        List<ProjectSuccessionCandidatePreview> candidates = collaboratorRepository
                .findActiveSuccessionCandidatePreviewsByProjectId(projectId).stream()
                .filter(candidate -> !candidate.getMemberId().equals(departingMemberId))
                .toList();

        Optional<ProjectSuccessionCandidatePreview> regularMember = candidates.stream()
                .filter(candidate -> candidate.getMemberRole() == Role.ROLE_USER)
                .min(REGULAR_MEMBER_PRIORITY);
                
        if (regularMember.isPresent()) {
            return regularMember;
        }

        List<ProjectSuccessionCandidatePreview> guests = candidates.stream()
                .filter(candidate -> candidate.getMemberRole() == Role.ROLE_GUEST)
                .toList();
        if (guests.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(guests.get(ThreadLocalRandom.current().nextInt(guests.size())));
    }
}
