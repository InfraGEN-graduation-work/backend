package com.infragen.infragen.domain.member.service.command;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.auth.service.TokenService;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorInvitationRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.service.command.ProjectCollaboratorInvitationCommandService;
import com.infragen.infragen.domain.project.service.command.ProjectOwnershipSuccessionCommandService;
import com.infragen.infragen.domain.project.service.command.ProjectOwnershipSuccessionPlan;
import com.infragen.infragen.global.apiPayload.code.GeneralErrorCode;
import com.infragen.infragen.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

/**
 * 일반 회원 탈퇴 때 그 회원의 프로젝트 관계를 정리하고 회원을 비활성화하는 전체 흐름을 조립한다.
 * 소유 프로젝트는 다른 참여자에게 승계하거나 후보가 없으면 삭제하고, 대기 초대는 취소하며,
 * 다른 프로젝트의 참여 기록은 제거한다. 세부 처리는 각 부품 서비스가 하고 이 서비스는 순서와 잠금을 맡는다.
 * 부품 서비스 세 개(MANDATORY)가 이 서비스가 여는 하나의 transaction 안에서만 돌기 때문에,
 * 중간에 실패하면 승계·삭제·취소·비활성화가 모두 함께 되돌려진다.
 * 아직 어떤 API에서도 호출하지 않는다. 기존 회원 탈퇴 API는 계속 {@link MemberCommandService#withdrawMember}를 쓴다.
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalCommandService {
    private final MemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectCollaboratorRepository collaboratorRepository;
    private final ProjectCollaboratorInvitationRepository invitationRepository;
    private final ProjectOwnershipSuccessionCommandService successionService;
    private final ProjectCollaboratorInvitationCommandService invitationCommandService;
    private final TokenService tokenService;

    /**
     * 일반 회원 한 명의 프로젝트 관계를 정리하고 회원을 비활성화한 뒤 refresh token을 삭제한다.
     * 이 서비스가 최상위 transaction을 열어야 READ_COMMITTED가 적용된다. 다른 transaction 안에서 호출하면
     * 바깥 transaction의 격리 수준을 따르므로 새 관계 재확인이 오래된 snapshot을 볼 수 있다.
     * Redis 삭제는 마지막에 하므로 삭제가 실패하면 DB 변경 전체가 롤백된다.
     *
     * @param memberId 탈퇴할 일반 회원 ID
     * @throws MemberException 회원이 없거나 이미 비활성이면 MEMBER_NOT_FOUND, guest면 GUEST_ACTION_NOT_ALLOWED
     * @throws GeneralException 잠금 뒤 새 프로젝트 관계가 생겼거나 정리 뒤에도 관계가 남으면 CONCURRENT_MODIFICATION
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdraw(Long memberId) {
        // 프로젝트를 먼저, 회원을 나중에 잠근다. 프로젝트 이전·초대 요청도 같은 순서로 잠가 서로 기다리다 멈추지 않게 한다.
        List<Long> projectIds = findRelatedProjectIds(memberId);
        projectIds.forEach(projectRepository::findByIdForUpdate);

        List<ProjectOwnershipSuccessionPlan> plans = successionService.prepareSuccessionPlans(memberId, projectIds);
        ensureWithdrawable(lockMembersInIdOrder(memberId, plans));

        // 회원 잠금을 얻은 뒤에는 이 회원을 새 owner·참여자·초대 대상으로 만드는 요청이 대기한다.
        // 잠금 전에 이미 커밋된 새 관계는 여기서 찾아 전체를 중단한다.
        ensureNoNewRelatedProject(memberId, projectIds);

        successionService.executeSuccessionPlans(plans);
        invitationCommandService.cancelRelatedPendingInvitations(memberId, projectIds);
        collaboratorRepository.deleteAllByMemberId(memberId);

        // 앞선 bulk 삭제가 영속성 context를 비웠으므로 잠금 조회로 회원을 다시 읽는다.
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
                
        member.withdraw();
        memberRepository.flush();

        ensureNoRemainingRelation(memberId);
        tokenService.deleteRefreshToken(memberId);
    }

    // 소유·참여·관련 대기 초대 project ID를 합쳐 중복 없이 오름차순으로 반환한다.
    private List<Long> findRelatedProjectIds(Long memberId) {
        return Stream.of(
                        projectRepository.findOwnedProjectIdsOrderByIdAsc(memberId),
                        collaboratorRepository.findParticipatingProjectIdsOrderByProjectIdAsc(memberId),
                        invitationRepository.findPendingRelatedProjectIdsOrderByProjectIdAsc(memberId))
                .flatMap(List::stream)
                .distinct()
                .sorted()
                .toList();
    }

    // 떠나는 회원과 승계 대상 회원을 ID 오름차순으로 잠근다. 승계 대상의 상태 검증은 승계 실행 단계가 맡는다.
    private Optional<Member> lockMembersInIdOrder(Long memberId, List<ProjectOwnershipSuccessionPlan> plans) {
        Set<Long> memberIds = new TreeSet<>();
        memberIds.add(memberId);
        for (ProjectOwnershipSuccessionPlan plan : plans) {
            if (plan instanceof ProjectOwnershipSuccessionPlan.Transfer transfer) {
                memberIds.add(transfer.successorMemberId());
            }
        }

        Optional<Member> departingMember = Optional.empty();
        for (Long id : memberIds) {
            Optional<Member> locked = memberRepository.findByIdForUpdate(id);
            if (id.equals(memberId)) {
                departingMember = locked;
            }
        }
        return departingMember;
    }

    private void ensureWithdrawable(Optional<Member> departingMember) {
        Member member = departingMember
                .filter(found -> Boolean.TRUE.equals(found.getIsActive()))
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        if (member.getRole() == Role.ROLE_GUEST) {
            throw new MemberException(MemberErrorCode.GUEST_ACTION_NOT_ALLOWED);
        }
    }

    // 처음 잠근 범위에 없는 project가 생겼다면 늦게 잠그지 않고 중단한다. 늦은 잠금은 다른 요청과 순서가 어긋난다.
    private void ensureNoNewRelatedProject(Long memberId, List<Long> lockedProjectIds) {
        if (!lockedProjectIds.containsAll(findRelatedProjectIds(memberId))) {
            throw new GeneralException(GeneralErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    // 정리 뒤에도 소유·참여·대기 초대 관계가 남았다면 회원을 비활성화한 채 커밋하지 않는다.
    private void ensureNoRemainingRelation(Long memberId) {
        if (!findRelatedProjectIds(memberId).isEmpty()) {
            throw new GeneralException(GeneralErrorCode.CONCURRENT_MODIFICATION);
        }
    }
}
