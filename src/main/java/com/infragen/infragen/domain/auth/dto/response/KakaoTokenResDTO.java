package com.infragen.infragen.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record KakaoTokenResDTO(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") Integer expiresIn,
        @JsonProperty("scope") String scope,
        @JsonProperty("refresh_token_expires_in") Integer refreshTokenExpiresIn
) {
}

