package com.infragen.infragen.domain.project.service.command;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;

/** 일반 회원 탈퇴에 필요한 소유 프로젝트 승계·삭제를 하나의 transaction으로 조정한다. */
@Service
@RequiredArgsConstructor
public class ProjectOwnershipSuccessionCommandService {
    private static final Comparator<ProjectCollaborator> REGULAR_MEMBER_PRIORITY = Comparator
            .comparingInt((ProjectCollaborator candidate) ->
                    candidate.getRole() == ProjectCollaboratorRole.EDITOR ? 0 : 1)
            .thenComparing(ProjectCollaborator::getCreatedAt)
            .thenComparing(ProjectCollaborator::getId);

    private final ProjectRepository projectRepository;
    private final ProjectCollaboratorRepository collaboratorRepository;
    private final MemberRepository memberRepository;
    private final ProjectCommandService projectCommandService;

    /**
     * 활성 일반 회원의 소유 프로젝트를 ID 순으로 잠가 승계하거나 삭제한다.
     * 떠나는 회원을 참여자로 남기지 않으며, 회원 비활성화·다른 프로젝트 참여 정리는 호출자가 맡는다.
     * 기존 transaction에서 호출할 때도 후보 조회를 위해 READ_COMMITTED를 사용해야 한다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void succeedOwnedProjectsOnWithdrawal(Long memberId) {
        Member departingMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        if (!Boolean.TRUE.equals(departingMember.getIsActive())) {
            throw new MemberException(MemberErrorCode.MEMBER_NOT_FOUND);
        }
        if (departingMember.getRole() == Role.ROLE_GUEST) {
            throw new MemberException(MemberErrorCode.GUEST_ACTION_NOT_ALLOWED);
        }

        // bulk delete가 캐시를 비우므로 project 객체를 미리 모아두지 않는다.
        for (Long projectId : projectRepository.findOwnedProjectIdsOrderByIdAsc(memberId)) {
            Optional<Project> lockedProject = projectRepository.findByIdForUpdate(projectId);
            if (lockedProject.isEmpty()
                    || !lockedProject.get().getMember().getId().equals(memberId)) {
                continue;
            }
            Project project = lockedProject.get();
            Optional<ProjectCollaborator> successor = selectSuccessor(projectId, memberId);
            if (successor.isEmpty()) {
                projectCommandService.deleteProject(projectId, memberId);
                continue;
            }

            ProjectCollaborator candidate = successor.get();
            Member newOwner = memberRepository.findByIdForUpdate(candidate.getMember().getId())
                    .orElseThrow(() -> new ProjectException(
                            ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE));
            if (!Boolean.TRUE.equals(newOwner.getIsActive())
                    || newOwner.getRole() != candidate.getMember().getRole()) {
                throw new ProjectException(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE);
            }
            if (collaboratorRepository.deleteByProjectIdAndMemberId(projectId, newOwner.getId()) != 1L) {
                throw new ProjectException(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE);
            }
            project.transferOwnershipTo(newOwner);
        }
    }

    private Optional<ProjectCollaborator> selectSuccessor(Long projectId, Long departingMemberId) {
        List<ProjectCollaborator> candidates = collaboratorRepository
                .findActiveSuccessionCandidatesByProjectId(projectId).stream()
                .filter(candidate -> Boolean.TRUE.equals(candidate.getMember().getIsActive()))
                .filter(candidate -> !candidate.getMember().getId().equals(departingMemberId))
                .toList();

        Optional<ProjectCollaborator> regularMember = candidates.stream()
                .filter(candidate -> candidate.getMember().getRole() == Role.ROLE_USER)
                .min(REGULAR_MEMBER_PRIORITY);
                
        if (regularMember.isPresent()) {
            return regularMember;
        }

        List<ProjectCollaborator> guests = candidates.stream()
                .filter(candidate -> candidate.getMember().getRole() == Role.ROLE_GUEST)
                .toList();
        if (guests.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(guests.get(ThreadLocalRandom.current().nextInt(guests.size())));
    }
}
