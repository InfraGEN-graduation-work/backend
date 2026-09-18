package com.infragen.infragen.domain.member.dto.response;

import java.time.LocalDateTime;

import com.infragen.infragen.domain.member.enums.Role;

import lombok.Builder;

public final class MemberResDTO {
    private MemberResDTO() {
    }

    @Builder
    public record InvitationCode(
            String inviteCode
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
