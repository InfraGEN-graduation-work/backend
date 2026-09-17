package com.infragen.infragen.domain.member.converter;

import com.infragen.infragen.domain.auth.dto.request.AuthReqDTO;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.enums.SocialProvider;

public class MemberConverter {
    private MemberConverter() {
    }

    /**
     * Member entity를 MemberResultDTO로 변환한다.
     * @param member
     * @return
     */
    public static MemberResDTO.MemberResultDTO toResultDTO(Member member) {
        return MemberResDTO.MemberResultDTO.builder()
                .id(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .role(member.getRole())
                .isActive(member.getIsActive())
                .createdAt(member.getCreatedAt())
                .build();
    }

    /**
     * 일반 회원가입 회원을 생성한다. (소셜 로그인과 달리 socialId·provider는 null)
     * @param request
     * @param encodedPassword
     * @return
     */
    public static Member toEntity(AuthReqDTO.SignupDTO request, String encodedPassword) {
        return Member.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .nickname(request.getNickname())
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();
    }

    /**
     * 소셜 로그인 회원을 생성한다. (소셜 로그인 시 socialId·provider를 저장)
     * @param email
     * @param nickname
     * @param socialId
     * @param provider
     * @param encodedPassword
     * @return
     */
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

    /**
     * guest member를 생성한다. (소셜 로그인과 달리 socialId·provider는 null)
     * @param email
     * @param nickname
     * @param encodedPassword
     * @return
     */
    public static Member toGuestEntity(String email, String nickname, String encodedPassword) {
        return Member.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .role(Role.ROLE_GUEST)
                .isActive(true)
                .build();
    }
}
