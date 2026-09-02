package com.infragen.infragen.domain.auth.service;

import com.infragen.infragen.domain.auth.dto.response.AuthResDTO;
import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.global.util.JwtUtil;
import com.infragen.infragen.global.util.RedisUtil;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {
    private static final String REDIS_RT_PREFIX = "RT:";
    private static final String BEARER_PREFIX = "bearer ";
    private static final String REFRESH_CATEGORY = "refresh";

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    /**
     * access token과 refresh token을 발급하고 refresh token을 저장한다.
     */
    public AuthResDTO.TokenResultDTO issueTokens(Long memberId, Role role) {
        String accessToken = jwtUtil.createAccessToken(memberId, role);
        String refreshToken = jwtUtil.createRefreshToken(memberId);

        Long expirationMillis = jwtUtil.getExpirationTime(refreshToken);
        redisUtil.set(
                refreshTokenKey(memberId),
                refreshToken,
                Duration.ofMillis(expirationMillis)
        );

        return new AuthResDTO.TokenResultDTO(accessToken, refreshToken);
    }

    /**
     * Authorization header 또는 cookie에서 전달된 token의 Bearer prefix를 제거한다.
     */
    public String resolveToken(String token) {
        if (token == null) {
            return null;
        }

        String trimmedToken = token.trim();
        if (trimmedToken.toLowerCase().startsWith(BEARER_PREFIX)) {
            return trimmedToken.substring(BEARER_PREFIX.length()).trim();
        }
        return trimmedToken;
    }

    /**
     * 로그아웃에 사용할 token에서 member ID를 추출한다. 만료된 token도 서명이 유효하면 허용한다.
     */
    public Long extractMemberIdForLogout(String accessToken) {
        Claims claims;
        try {
            claims = jwtUtil.getClaimsForLogout(accessToken);
        } catch (RuntimeException e) {
            throw invalidToken();
        }

        return parseMemberId(claims);
    }

    /**
     * refresh token을 검증하고 Redis에서 원자적으로 소비한 뒤 member ID를 반환한다.
     */
    public Long consumeRefreshToken(String refreshToken) {
        String resolvedToken = resolveToken(refreshToken);
        Claims claims = getValidatedClaims(resolvedToken);

        Long memberId = parseMemberId(claims);
        Object storedRefreshToken = redisUtil.getAndDelete(refreshTokenKey(memberId));
        if (!(storedRefreshToken instanceof String storedToken)
                || !storedToken.equals(resolvedToken)) {
            log.warn("토큰 재발급 실패: 탈취 의심 / memberId={}", memberId);
            throw invalidToken();
        }

        return memberId;
    }

    /**
     * member의 refresh token을 삭제한다.
     */
    public void deleteRefreshToken(Long memberId) {
        redisUtil.delete(refreshTokenKey(memberId));
    }

    /**
     * access token을 남은 유효 시간만큼 blacklist에 등록한다.
     */
    public void blacklistAccessToken(String accessToken) {
        redisUtil.setBlackList(accessToken, jwtUtil.getExpirationTime(accessToken));
    }

    private Claims getValidatedClaims(String token) {
        if (!jwtUtil.validateToken(token)) {
            throw invalidToken();
        }

        try {
            Claims claims = jwtUtil.getClaims(token);
            if (!REFRESH_CATEGORY.equals(claims.get("category", String.class))) {
                throw invalidToken();
            }
            return claims;
        } catch (AuthException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalidToken();
        }
    }

    private Long parseMemberId(Claims claims) {
        String subject = claims == null ? null : claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw invalidToken();
        }

        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw invalidToken();
        }
    }

    private String refreshTokenKey(Long memberId) {
        return REDIS_RT_PREFIX + memberId;
    }

    private AuthException invalidToken() {
        return new AuthException(AuthErrorCode.TOKEN_INVALID);
    }
}
