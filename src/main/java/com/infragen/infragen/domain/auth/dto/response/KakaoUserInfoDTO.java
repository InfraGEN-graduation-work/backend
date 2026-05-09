package com.infragen.infragen.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.infragen.infragen.domain.auth.client.OAuth2UserInfo;
import lombok.Builder;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserInfoDTO(
        @JsonProperty("id") Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) implements OAuth2UserInfo {

    @Override
    public String getSocialId() {
        return String.valueOf(id);
    }

    @Override
    public String getEmail() {
        return (kakaoAccount != null) ? kakaoAccount.email() : null;
    }

    // 회원의 id 반환
    @Override
    public String getNickname() {
        if (kakaoAccount != null && kakaoAccount.profile() != null) {
            return kakaoAccount.profile().nickname();
        }
        return "User_" + id;
    }

    // 카카오 extra 정보
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(
            String email,
            Profile profile
    ) {
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Profile(String nickname) {}
    }
}

