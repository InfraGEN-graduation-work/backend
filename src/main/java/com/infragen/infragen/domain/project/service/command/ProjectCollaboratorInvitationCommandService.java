package com.infragen.infragen.domain.project.service.command;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
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

/** 프로젝트 협업 초대 발신과 수락·거절을 transaction으로 처리한다. */
@Service
@RequiredArgsConstructor
public class ProjectCollaboratorInvitationCommandService {
    private static final long INVITATION_VALIDITY_HOURS = 24L; // 초대 유효기간을 24시간으로 설정한다.

    private final ProjectRepository projectRepository;
    private final MemberQueryService memberQueryService;
    private final ProjectCollaboratorRepository collaboratorRepository;
    private final ProjectCollaboratorInvitationRepository invitationRepository;

    /** owner가 활성 회원을 초대하고, 초대 상태를 PENDING으로 저장한다.
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
        LocalDateTime now = LocalDateTime.now();
        Project project = findOwnedProjectForUpdate(projectId, ownerId);
        Member projectOwner = project.getMember();
        Member invitee = findInvitee(inviteeCode);

        if (projectOwner.getId().equals(invitee.getId())) {
            throw new ProjectException(ProjectErrorCode.OWNER_CANNOT_BE_COLLABORATOR);
        }
        ensureInviteeCanJoin(projectOwner, invitee);
        ensureNotCollaborator(projectId, invitee.getId());
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

    /** 인증된 초대 대상이 수락하면 초대 상태와 collaborator membership을 함께 저장한다. */
    @Transactional
    public void accept(Long invitationId, Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        ProjectCollaboratorInvitation invitation = findRespondableInvitation(
                invitationId,
                memberId,
                now
        );
        Long projectId = invitation.getProject().getId();

        ensureNotCollaborator(projectId, memberId);
        invitation.accept(invitation.getInvitee(), now);
        collaboratorRepository.save(ProjectCollaborator.builder()
                .project(invitation.getProject())
                .member(invitation.getInvitee())
                .role(invitation.getRole())
                .build());
    }

    /** 인증된 초대 대상이 거절하면 초대 상태만 변경한다. */
    @Transactional
    public void decline(Long invitationId, Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        ProjectCollaboratorInvitation invitation = findRespondableInvitation(
                invitationId,
                memberId,
                now
        );

        invitation.decline(invitation.getInvitee(), now);
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

    // 초대 대상이 프로젝트에 참여할 수 있는지 확인한다.
    private void ensureInviteeCanJoin(Member projectOwner, Member invitee) {
        if (projectOwner.getRole() == Role.ROLE_GUEST
                && invitee.getRole() != Role.ROLE_GUEST) {
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

    // 응답 가능한 초대를 조회한다. PENDING 응답 경쟁을 직렬화하도록 row lock을 유지한다.
    private ProjectCollaboratorInvitation findRespondableInvitation(
            Long invitationId,
            Long memberId,
            LocalDateTime now
    ) {
        ProjectCollaboratorInvitation invitation = invitationRepository
                .findByIdAndInviteeIdForUpdate(invitationId, memberId)
                .orElseThrow(() -> new ProjectException(
                        ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE
                ));

        if (invitation.getStatus() != ProjectCollaboratorInvitationStatus.PENDING
                || !invitation.getExpiresAt().isAfter(now)) {
            throw new ProjectException(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE);
        }
        return invitation;
    }
}
