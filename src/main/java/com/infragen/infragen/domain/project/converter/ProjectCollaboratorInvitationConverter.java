package com.infragen.infragen.domain.project.converter;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaboratorInvitation;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import java.time.LocalDateTime;
import java.util.List;

public final class ProjectCollaboratorInvitationConverter {
    private ProjectCollaboratorInvitationConverter() {
    }

    /** 프로젝트 초대의 발신자, 대상, 역할, 만료 시각을 Entity로 조립한다. */
    public static ProjectCollaboratorInvitation toEntity(
            Project project,
            Member invitedBy,
            Member invitee,
            ProjectCollaboratorRole role,
            LocalDateTime expiresAt
    ) {
        return ProjectCollaboratorInvitation.builder()
                .project(project)
                .invitedBy(invitedBy)
                .invitee(invitee)
                .role(role)
                .expiresAt(expiresAt)
                .build();
    }

    /** 초대 발신자가 보는 정보로 변환한다. 대상 회원의 내부 ID와 초대코드는 응답하지 않는다. */
    public static ProjectCollaboratorInvitationResDTO.SentItem toSentItem(
            ProjectCollaboratorInvitation invitation,
            ProjectCollaboratorInvitationResDTO.InvitationStatus status
    ) {
        return ProjectCollaboratorInvitationResDTO.SentItem.builder()
                .invitationId(invitation.getId())
                .inviteeNickname(invitation.getInvitee().getNickname())
                .role(invitation.getRole())
                .status(status)
                .createdAt(invitation.getCreatedAt())
                .expiresAt(invitation.getExpiresAt())
                .respondedAt(invitation.getRespondedAt())
                .build();
    }

    /** 초대 대상자가 확인할 프로젝트와 초대자 정보로 변환한다. */
    public static ProjectCollaboratorInvitationResDTO.ReceivedItem toReceivedItem(
            ProjectCollaboratorInvitation invitation,
            ProjectCollaboratorInvitationResDTO.InvitationStatus status
    ) {
        return ProjectCollaboratorInvitationResDTO.ReceivedItem.builder()
                .invitationId(invitation.getId())
                .projectTitle(invitation.getProject().getTitle())
                .inviterNickname(invitation.getInvitedBy().getNickname())
                .role(invitation.getRole())
                .status(status)
                .createdAt(invitation.getCreatedAt())
                .expiresAt(invitation.getExpiresAt())
                .build();
    }

    public static ProjectCollaboratorInvitationResDTO.SentList toSentList(
            List<ProjectCollaboratorInvitationResDTO.SentItem> invitations
    ) {
        return ProjectCollaboratorInvitationResDTO.SentList.builder()
                .invitations(invitations)
                .build();
    }

    public static ProjectCollaboratorInvitationResDTO.ReceivedList toReceivedList(
            List<ProjectCollaboratorInvitationResDTO.ReceivedItem> invitations
    ) {
        return ProjectCollaboratorInvitationResDTO.ReceivedList.builder()
                .invitations(invitations)
                .build();
    }
}
