package com.infragen.infragen.domain.project.service.query;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.project.converter.ProjectCollaboratorInvitationConverter;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorInvitationStatus;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorInvitationRepository;

import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectCollaboratorInvitationQueryService {
    private final ProjectQueryService projectQueryService;
    private final ProjectCollaboratorInvitationRepository invitationRepository;

    /** 현재 owner의 발신 목록을 조회한다. 탈퇴한 대상의 이력을 보존하며 상태·시각은 기존 계약을 따른다. */
    @Transactional(readOnly = true)
    public ProjectCollaboratorInvitationResDTO.SentList getSentInvitations(
            Long projectId,
            Long ownerId
    ) {
        projectQueryService.getOwnedProject(projectId, ownerId);
        LocalDateTime now = LocalDateTime.now();
        List<ProjectCollaboratorInvitationResDTO.SentItem> invitations = invitationRepository
                .findSentHistoryByProjectId(projectId)
                .stream()
                .map(invitation -> ProjectCollaboratorInvitationConverter.toSentItem(
                        invitation,
                        resolveStatus(invitation, now)
                ))
                .toList();

        return ProjectCollaboratorInvitationConverter.toSentList(invitations);
    }

    /** 인증 회원에게 온 이력을 조회하며 탈퇴한 초대자도 표시한다. 상태가 null이면 전체를 반환한다. */
    @Transactional(readOnly = true)
    public ProjectCollaboratorInvitationResDTO.ReceivedList getReceivedInvitations(
            Long memberId,
            ProjectCollaboratorInvitationResDTO.InvitationStatus statusFilter
    ) {
        LocalDateTime now = LocalDateTime.now();
        
        List<ProjectCollaboratorInvitationResDTO.ReceivedItem> invitations = invitationRepository
                .findReceivedHistoryByInviteeId(memberId)
                .stream()
                .filter(invitation -> statusFilter == null
                        || resolveStatus(invitation, now) == statusFilter)
                .map(invitation -> ProjectCollaboratorInvitationConverter.toReceivedItem(
                        invitation,
                        resolveStatus(invitation, now)
                ))
                .toList();

        return ProjectCollaboratorInvitationConverter.toReceivedList(invitations);
    }

    // PENDING 상태의 초대는 만료 여부를 확인하고 EXPIRED 상태는 만료된 대기만 반환한다.
    private ProjectCollaboratorInvitationResDTO.InvitationStatus resolveStatus(
            Tuple invitation,
            LocalDateTime now
    ) {
        ProjectCollaboratorInvitationStatus status = ProjectCollaboratorInvitationStatus.valueOf(
                invitation.get("status", String.class));

        if (status == ProjectCollaboratorInvitationStatus.PENDING
                && !ProjectCollaboratorInvitationConverter.readDateTime(invitation, "expiresAt").isAfter(now)) {
            return ProjectCollaboratorInvitationResDTO.InvitationStatus.EXPIRED;
        }

        return ProjectCollaboratorInvitationResDTO.InvitationStatus.valueOf(status.name());
    }
}
