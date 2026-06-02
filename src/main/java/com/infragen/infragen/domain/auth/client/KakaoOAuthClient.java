package com.infragen.infragen.domain.auth.client;

import com.infragen.infragen.domain.auth.dto.response.KakaoTokenResDTO;
import com.infragen.infragen.domain.auth.dto.response.KakaoUserInfoDTO;
import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import com.infragen.infragen.global.properties.KakaoProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

    private final RestClient restClient;
    private final KakaoProperties kakaoProperties;

    public KakaoTokenResDTO fetchKakaoAccessToken(String authorizationCode) {
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "authorization_code");
            body.add("client_id", kakaoProperties.getClientId());
            body.add("redirect_uri", kakaoProperties.getRedirectUri());
            body.add("code", authorizationCode);

            return restClient.post()
                    .uri(kakaoProperties.getTokenUri())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=utf-8")
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.error("카카오 토큰 발급 4xx 에러: {}", res.getStatusCode());
                        throw new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.error("카카오 토큰 발급 5xx 에러: {}", res.getStatusCode());
                        throw new AuthException(AuthErrorCode.SOCIAL_COMMUNICATION_ERROR);
                    })
                    .body(KakaoTokenResDTO.class);
        } catch (RestClientException e) {
            log.error("카카오 토큰 API 통신 실패: {}", e.getMessage());
            throw new AuthException(AuthErrorCode.SOCIAL_COMMUNICATION_ERROR);
        }
    }

    public KakaoUserInfoDTO fetchKakaoUserInfo(String accessToken) {
        try {
            return restClient.get()
                    .uri(kakaoProperties.getUserInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.error("카카오 유저 조회 4xx 에러: {}", res.getStatusCode());
                        throw new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.error("카카오 유저 조회 5xx 에러: {}", res.getStatusCode());
                        throw new AuthException(AuthErrorCode.SOCIAL_COMMUNICATION_ERROR);
                    })
                    .body(KakaoUserInfoDTO.class);
        } catch (RestClientException e) {
            log.error("카카오 유저 조회 API 통신 실패: {}", e.getMessage());
            throw new AuthException(AuthErrorCode.SOCIAL_COMMUNICATION_ERROR);
        }
    }
}
