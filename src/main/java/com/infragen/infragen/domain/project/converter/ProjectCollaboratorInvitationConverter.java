package com.infragen.infragen.domain.project.converter;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaboratorInvitation;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
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

    /** 발신 이력의 scalar 조회 값을 응답으로 변환한다. 회원 ID·초대코드를 노출하거나 Entity를 조회하지 않는다. */
    public static ProjectCollaboratorInvitationResDTO.SentItem toSentItem(
            Tuple invitation,
            ProjectCollaboratorInvitationResDTO.InvitationStatus status
    ) {
        return ProjectCollaboratorInvitationResDTO.SentItem.builder()
                .invitationId(invitation.get("invitationId", Number.class).longValue())
                .inviteeNickname(invitation.get("memberNickname", String.class))
                .role(ProjectCollaboratorRole.valueOf(invitation.get("role", String.class)))
                .status(status)
                .createdAt(readDateTime(invitation, "createdAt"))
                .expiresAt(readDateTime(invitation, "expiresAt"))
                .respondedAt(readDateTime(invitation, "respondedAt"))
                .build();
    }

    /** 수신 이력의 프로젝트·초대자 표시 값을 응답으로 변환한다. 표시 상태는 호출자가 판단한다. */
    public static ProjectCollaboratorInvitationResDTO.ReceivedItem toReceivedItem(
            Tuple invitation,
            ProjectCollaboratorInvitationResDTO.InvitationStatus status
    ) {
        return ProjectCollaboratorInvitationResDTO.ReceivedItem.builder()
                .invitationId(invitation.get("invitationId", Number.class).longValue())
                .projectTitle(invitation.get("projectTitle", String.class))
                .inviterNickname(invitation.get("memberNickname", String.class))
                .role(ProjectCollaboratorRole.valueOf(invitation.get("role", String.class)))
                .status(status)
                .createdAt(readDateTime(invitation, "createdAt"))
                .expiresAt(readDateTime(invitation, "expiresAt"))
                .build();
    }

    /** native 조회의 Timestamp·LocalDateTime을 기존 응답 시각으로 변환하며 선택 필드의 null은 유지한다. */
    public static LocalDateTime readDateTime(Tuple row, String alias) {
        Object value = row.get(alias);
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return (LocalDateTime) value;
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
