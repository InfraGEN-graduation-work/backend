package com.infragen.infragen.domain.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class AuthResDTO {

    // Service -> Controller 내부 전달용
    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenResultDTO {
        private String accessToken;
        private String refreshToken;
    }

    // Controller -> Client 응답용
    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessTokenResultDTO {
        private String accessToken;
    }
}

