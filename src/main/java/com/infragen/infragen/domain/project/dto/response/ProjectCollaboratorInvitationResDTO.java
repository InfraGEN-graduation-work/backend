package com.infragen.infragen.domain.project.dto.response;

import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

public final class ProjectCollaboratorInvitationResDTO {
    private ProjectCollaboratorInvitationResDTO() {
    }

    public enum InvitationStatus {
        PENDING,
        ACCEPTED,
        DECLINED,
        EXPIRED
    }

    @Builder
    public record SentList(List<SentItem> invitations) {
    }

    @Builder
    public record SentItem(
            Long invitationId,
            String inviteeNickname,
            ProjectCollaboratorRole role,
            InvitationStatus status,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            LocalDateTime respondedAt
    ) {
    }

    @Builder
    public record ReceivedList(List<ReceivedItem> invitations) {
    }

    @Builder
    public record ReceivedItem(
            Long invitationId,
            String projectTitle,
            String inviterNickname,
            ProjectCollaboratorRole role,
            InvitationStatus status,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
    }
}
