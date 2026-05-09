package com.infragen.infragen.domain.member.converter;

import com.infragen.infragen.domain.auth.dto.request.AuthReqDTO;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.enums.SocialProvider;

public class MemberConverter {
    public static MemberResDTO.MemberResultDTO toResultDTO(Member member) {
        return MemberResDTO.MemberResultDTO.builder()
                .id(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .role(member.getRole())
                .createdAt(member.getCreatedAt())
                .build();
    }

    public static Member toEntity(AuthReqDTO.SignupDTO request, String encodedPassword) {
        return Member.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .nickname(request.getNickname())
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();
    }

    public static Member toSocialEntity(String email, String nickname, String socialId, SocialProvider provider, String encodedPassword) {
        return Member.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .role(Role.ROLE_USER)
                .isActive(true)
                .socialProvider(provider)
                .socialId(socialId)
                .build();
    }
}
