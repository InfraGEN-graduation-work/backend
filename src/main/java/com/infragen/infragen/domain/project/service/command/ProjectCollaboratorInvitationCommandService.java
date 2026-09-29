package com.infragen.infragen.domain.project.service.command;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.converter.ProjectCollaboratorInvitationConverter;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.entity.ProjectCollaboratorInvitation;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorInvitationStatus;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorInvitationRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;

/** 프로젝트 협업 초대 발신·응답과 탈퇴 관련 대기 초대 취소를 transaction으로 처리한다. */
@Service
@RequiredArgsConstructor
public class ProjectCollaboratorInvitationCommandService {
    private static final long INVITATION_VALIDITY_HOURS = 24L; // 초대 유효기간을 24시간으로 설정한다.

    private final ProjectRepository projectRepository;
    private final MemberQueryService memberQueryService;
    private final MemberRepository memberRepository;
    private final ProjectCollaboratorRepository collaboratorRepository;
    private final ProjectCollaboratorInvitationRepository invitationRepository;

    /** owner가 같은 계정 유형의 활성 회원을 초대하고, 초대 상태를 PENDING으로 저장한다.
     * project 다음 두 회원을 ID 순으로 잠그며, 코드 조회 결과는 대상 식별에만 사용한다.
     *
     * @param projectId 초대할 프로젝트 식별자
     * @param ownerId 프로젝트 소유자 회원 식별자
     * @param inviteeCode 초대 대상 회원의 초대 코드
     * @param role 초대 대상에게 부여할 역할
     */
    @Transactional
    public void invite(
            Long projectId,
            Long ownerId,
            String inviteeCode,
            ProjectCollaboratorRole role
    ) {
        Project project = findOwnedProjectForUpdate(projectId, ownerId);
        Long inviteeId = findInvitee(inviteeCode).getId();

        if (ownerId.equals(inviteeId)) {
            throw new ProjectException(ProjectErrorCode.OWNER_CANNOT_BE_COLLABORATOR);
        }

        Member projectOwner;
        Member invitee;

        // 두 회원을 참조하는 발신·이전 요청의 회원 잠금 순서를 맞춘다.
        if (ownerId < inviteeId) {
            projectOwner = findActiveMemberForUpdate(ownerId, ProjectErrorCode.PROJECT_NOT_FOUND);
            invitee = findActiveMemberForUpdate(inviteeId, ProjectErrorCode.COLLABORATOR_INVITATION_TARGET_UNAVAILABLE);
        } else {
            invitee = findActiveMemberForUpdate(inviteeId, ProjectErrorCode.COLLABORATOR_INVITATION_TARGET_UNAVAILABLE);
            projectOwner = findActiveMemberForUpdate(ownerId, ProjectErrorCode.PROJECT_NOT_FOUND);
        }

        ensureInviteeCanJoin(projectOwner, invitee);
        ensureNotCollaborator(projectId, invitee.getId());
        LocalDateTime now = LocalDateTime.now();
        ensureNoPendingInvitation(projectId, invitee.getId(), now);

        LocalDateTime expiresAt = now.plusHours(INVITATION_VALIDITY_HOURS);
        invitationRepository.save(ProjectCollaboratorInvitationConverter.toEntity(
                project,
                projectOwner,
                invitee,
                role,
                expiresAt
        ));
    }

    /**
     * 초대받은 활성 회원이 만료되지 않은 대기 초대를 수락해 참여자로 등록된다.
     * 현재 프로젝트 소유자와 같은 계정 유형만 수락할 수 있다.
     */
    @Transactional
    public void accept(Long invitationId, Long memberId) {
        ProjectCollaboratorInvitation invitation = findRespondableInvitation(
                invitationId,
                memberId
        );
        Long projectId = invitation.getProject().getId();
        Member projectOwner = invitation.getProject().getMember();

        // 기존 대기 초대도 수락 시점의 owner 정책을 통과해야 한다.
        if (projectOwner.getId().equals(memberId)
                || projectOwner.getRole() != invitation.getInvitee().getRole()) {
            throw new ProjectException(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE);
        }
        
        ensureNotCollaborator(projectId, memberId);
        invitation.accept(invitation.getInvitee(), LocalDateTime.now());
        collaboratorRepository.save(ProjectCollaborator.builder()
                .project(invitation.getProject())
                .member(invitation.getInvitee())
                .role(invitation.getRole())
                .build());
    }

    /** 초대받은 활성 회원이 만료되지 않은 대기 초대를 거절한다. 참여자 정보는 변경하지 않는다. */
    @Transactional
    public void decline(Long invitationId, Long memberId) {
        ProjectCollaboratorInvitation invitation = findRespondableInvitation(
                invitationId,
                memberId
        );

        invitation.decline(invitation.getInvitee(), LocalDateTime.now());
    }

    /**
     * 회원 비활성화 전에 관련 PENDING 초대만 취소하고 완료 이력은 보존한다.
     * project ID 순으로 project→invitation을 잠그며 기존 transaction에서도 READ_COMMITTED를 사용한다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelRelatedPendingInvitationsOnWithdrawal(Long memberId) {
        Member departingMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        if (!Boolean.TRUE.equals(departingMember.getIsActive())) {
            throw new MemberException(MemberErrorCode.MEMBER_NOT_FOUND);
        }

        for (Long projectId : invitationRepository.findPendingRelatedProjectIdsOrderByProjectIdAsc(memberId)) {
            if (projectRepository.findByIdForUpdate(projectId).isEmpty()) {
                continue;
            }
            List<ProjectCollaboratorInvitation> invitations = invitationRepository
                    .findPendingRelatedByProjectIdAndMemberIdForUpdate(projectId, memberId);
            LocalDateTime cancelledAt = LocalDateTime.now();
            for (ProjectCollaboratorInvitation invitation : invitations) {
                if (invitation.getStatus() != ProjectCollaboratorInvitationStatus.PENDING
                        || !invitation.getProject().getId().equals(projectId)
                        || (!invitation.getInvitedBy().getId().equals(memberId)
                                && !invitation.getInvitee().getId().equals(memberId))) {
                    continue;
                }
                invitation.cancel(departingMember, cancelledAt);
            }
        }
    }

    // 프로젝트 소유자가 소유한 프로젝트를 잠근 상태로 조회한다.
    private Project findOwnedProjectForUpdate(Long projectId, Long ownerId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!ownerId.equals(project.getMember().getId())) {
            throw new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }

        return project;
    }

    // 초대 대상 회원을 초대 코드로 조회한다.
    private Member findInvitee(String inviteeCode) {
        try {
            return memberQueryService.findByInvitationCode(inviteeCode);
        } catch (MemberException exception) {
            if (exception.getCode() != MemberErrorCode.MEMBER_NOT_FOUND) {
                throw exception;
            }
            throw new ProjectException(ProjectErrorCode.COLLABORATOR_INVITATION_TARGET_UNAVAILABLE);
        }
    }

    private Member findActiveMemberForUpdate(Long memberId, ProjectErrorCode errorCode) {
        return memberRepository.findByIdForUpdate(memberId)
                .filter(member -> Boolean.TRUE.equals(member.getIsActive()))
                .orElseThrow(() -> new ProjectException(errorCode));
    }

    // 계정 유형이 다른 새 초대는 발신하지 않는다.
    private void ensureInviteeCanJoin(Member projectOwner, Member invitee) {
        if (projectOwner.getRole() != invitee.getRole()) {
            throw new ProjectException(ProjectErrorCode.COLLABORATOR_INVITATION_TARGET_UNAVAILABLE);
        }
    }

    private void ensureNotCollaborator(Long projectId, Long memberId) {
        if (collaboratorRepository.existsByProjectIdAndMemberId(projectId, memberId)) {
            throw new ProjectException(ProjectErrorCode.COLLABORATOR_ALREADY_EXISTS);
        }
    }

    // 같은 프로젝트·초대 대상에 만료되지 않은 PENDING 초대가 있는지 확인한다.
    private void ensureNoPendingInvitation(Long projectId, Long inviteeId, LocalDateTime now) {
        boolean hasPendingInvitation = invitationRepository
                .existsByProjectIdAndInviteeIdAndStatusAndExpiresAtAfter(
                        projectId,
                        inviteeId,
                        ProjectCollaboratorInvitationStatus.PENDING,
                        now
                );
        if (hasPendingInvitation) {
            throw new ProjectException(ProjectErrorCode.COLLABORATOR_INVITATION_ALREADY_PENDING);
        }
    }

    // 잠금을 기다리는 동안 초대가 처리되거나 만료될 수 있으므로, 잠금 조회 후 상태를 검사한다.
    private ProjectCollaboratorInvitation findRespondableInvitation(
            Long invitationId,
            Long memberId
    ) {
        Long projectId = invitationRepository.findProjectIdByIdAndInviteeId(invitationId, memberId)
                .orElseThrow(() -> new ProjectException(
                        ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE));
        
        // 초대를 처리하는 동안 프로젝트가 삭제되거나 소유자가 바뀌지 않도록 해당 project 행을 먼저 잠근다.
        projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ProjectException(
                        ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE));

        // 수락·거절을 요청한 회원의 member 행을 잠가, 처리 도중 is_active가 변경되지 않게 한다.
        findActiveMemberForUpdate(memberId, ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE);

        // 이 초대의 상태 변경·삭제와 충돌하지 않도록 project_collaborator_invitation 행을 잠근다.
        ProjectCollaboratorInvitation invitation = invitationRepository
                .findByIdAndInviteeIdForUpdate(invitationId, memberId)
                .orElseThrow(() -> new ProjectException(
                        ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE
                ));

        if (!invitation.getProject().getId().equals(projectId)
                || !invitation.getInvitee().getId().equals(memberId)
                || !Boolean.TRUE.equals(invitation.getInvitee().getIsActive())
                || invitation.getStatus() != ProjectCollaboratorInvitationStatus.PENDING
                || !invitation.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ProjectException(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE);
        }
        return invitation;
    }
}
