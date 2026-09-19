package com.infragen.infragen.domain.project.service.query;

import com.infragen.infragen.domain.project.converter.ProjectCollaboratorInvitationConverter;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO;
import com.infragen.infragen.domain.project.entity.ProjectCollaboratorInvitation;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorInvitationStatus;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorInvitationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectCollaboratorInvitationQueryService {
    private final ProjectQueryService projectQueryService;
    private final ProjectCollaboratorInvitationRepository invitationRepository;

    /** owner가 관리하는 프로젝트의 발신 초대와 현재 표시 상태를 조회한다. */
    @Transactional(readOnly = true)
    public ProjectCollaboratorInvitationResDTO.SentList getSentInvitations(
            Long projectId,
            Long ownerId
    ) {
        projectQueryService.getOwnedProject(projectId, ownerId);
        LocalDateTime now = LocalDateTime.now();
        List<ProjectCollaboratorInvitationResDTO.SentItem> invitations = invitationRepository
                .findAllByProjectIdOrderByCreatedAtDescIdDesc(projectId)
                .stream()
                .map(invitation -> ProjectCollaboratorInvitationConverter.toSentItem(
                        invitation,
                        resolveStatus(invitation, now)
                ))
                .toList();

        return ProjectCollaboratorInvitationConverter.toSentList(invitations);
    }

    /** 현재 회원에게 온 초대를 선택 상태로 조회한다. 상태가 null이면 전체를 반환한다. */
    @Transactional(readOnly = true)
    public ProjectCollaboratorInvitationResDTO.ReceivedList getReceivedInvitations(
            Long memberId,
            ProjectCollaboratorInvitationResDTO.InvitationStatus statusFilter
    ) {
        LocalDateTime now = LocalDateTime.now();
        List<ProjectCollaboratorInvitationResDTO.ReceivedItem> invitations = invitationRepository
                .findAllByInviteeIdOrderByCreatedAtDescIdDesc(memberId)
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

    private ProjectCollaboratorInvitationResDTO.InvitationStatus resolveStatus(
            ProjectCollaboratorInvitation invitation,
            LocalDateTime now
    ) {
        if (invitation.getStatus() == ProjectCollaboratorInvitationStatus.PENDING
                && !invitation.getExpiresAt().isAfter(now)) {
            return ProjectCollaboratorInvitationResDTO.InvitationStatus.EXPIRED;
        }
        return ProjectCollaboratorInvitationResDTO.InvitationStatus.valueOf(
                invitation.getStatus().name()
        );
    }
}
