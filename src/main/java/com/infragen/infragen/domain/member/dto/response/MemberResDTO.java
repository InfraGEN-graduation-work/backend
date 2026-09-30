package com.infragen.infragen.domain.member.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.enums.WithdrawalProjectOutcome;

import lombok.Builder;

public final class MemberResDTO {
    private MemberResDTO() {
    }

    @Builder
    public record InvitationCode(
            String inviteCode
    ) {}

    @Builder
    public record WithdrawalPreview(
            List<WithdrawalOwnedProject> ownedProjects
    ) {}

    @Builder
    public record WithdrawalOwnedProject(
            Long projectId,
            String title,
            WithdrawalProjectOutcome outcome
    ) {}

    @Builder
    public record MemberResultDTO(
            Long id,
            String email,
            String nickname,
            Role role,
            Boolean isActive,
            LocalDateTime createdAt
    ) {}
}
