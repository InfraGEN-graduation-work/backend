package com.infragen.infragen.domain.auth.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infragen.infragen.domain.auth.dto.response.AuthResDTO;
import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.global.util.JwtUtil;
import com.infragen.infragen.global.util.RedisUtil;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RedisUtil redisUtil;
    @InjectMocks
    private TokenService tokenService;

    @Test
    @DisplayName("토큰 발급 - access·refresh token을 발급하고 refresh token을 저장")
    void issueTokens_Success() {
        // given
        when(jwtUtil.createAccessToken(1L, Role.ROLE_USER)).thenReturn("access_token");
        when(jwtUtil.createRefreshToken(1L)).thenReturn("refresh_token");
        when(jwtUtil.getExpirationTime("refresh_token")).thenReturn(1000L);

        // when
        AuthResDTO.TokenResultDTO result = tokenService.issueTokens(1L, Role.ROLE_USER);

        // then
        assertAll(
                () -> assertEquals("access_token", result.getAccessToken()),
                () -> assertEquals("refresh_token", result.getRefreshToken())
        );
        verify(redisUtil).set("RT:1", "refresh_token", Duration.ofMillis(1000L));
    }

    @Test
    @DisplayName("로그아웃 토큰 subject 검증 - 빈 subject면 인증 예외 발생")
    void extractMemberIdForLogout_BlankSubject_ThrowsAuthException() {
        // given
        Claims claims = mock(Claims.class);
        when(jwtUtil.getClaimsForLogout("access_token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(" ");

        // when
        AuthException exception = assertThrows(
                AuthException.class,
                () -> tokenService.extractMemberIdForLogout("access_token")
        );

        // then
        assertEquals(AuthErrorCode.TOKEN_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("로그아웃 토큰 subject 검증 - 숫자 subject를 member ID로 반환")
    void extractMemberIdForLogout_Success() {
        // given
        Claims claims = mock(Claims.class);
        when(jwtUtil.getClaimsForLogout("access_token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("1");

        // when
        Long memberId = tokenService.extractMemberIdForLogout("access_token");

        // then
        assertEquals(1L, memberId);
    }

    @Test
    @DisplayName("refresh token 소비 - 검증된 token을 Redis에서 원자적으로 삭제")
    void consumeRefreshToken_Success() {
        // given
        Claims claims = mock(Claims.class);
        when(jwtUtil.validateToken("refresh_token")).thenReturn(true);
        when(jwtUtil.getClaims("refresh_token")).thenReturn(claims);
        when(claims.get("category", String.class)).thenReturn("refresh");
        when(claims.getSubject()).thenReturn("1");
        when(redisUtil.getAndDelete("RT:1")).thenReturn("refresh_token");

        // when
        Long memberId = tokenService.consumeRefreshToken("Bearer refresh_token");

        // then
        assertEquals(1L, memberId);
        verify(redisUtil).getAndDelete("RT:1");
    }

    @Test
    @DisplayName("refresh token 재사용 - 저장된 token과 다르면 인증 예외 발생")
    void consumeRefreshToken_ReusedToken_ThrowsAuthException() {
        // given
        Claims claims = mock(Claims.class);
        when(jwtUtil.validateToken("refresh_token")).thenReturn(true);
        when(jwtUtil.getClaims("refresh_token")).thenReturn(claims);
        when(claims.get("category", String.class)).thenReturn("refresh");
        when(claims.getSubject()).thenReturn("1");
        when(redisUtil.getAndDelete("RT:1")).thenReturn(null);

        // when
        AuthException exception = assertThrows(
                AuthException.class,
                () -> tokenService.consumeRefreshToken("refresh_token")
        );

        // then
        assertEquals(AuthErrorCode.TOKEN_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("access token blacklist 등록 - 남은 유효 시간을 사용")
    void blacklistAccessToken_Success() {
        // given
        when(jwtUtil.getExpirationTime("access_token")).thenReturn(500L);

        // when
        tokenService.blacklistAccessToken("access_token");

        // then
        verify(redisUtil).setBlackList(eq("access_token"), eq(500L));
    }
}
