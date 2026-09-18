package com.infragen.infragen.global.auth.websocket;

import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.global.auth.CustomUserDetails;
import com.infragen.infragen.global.auth.CustomUserDetailsService;
import com.infragen.infragen.global.util.JwtUtil;
import com.infragen.infragen.global.util.RedisUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAccessTokenAuthenticatorTest {
    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("유효한 access token을 인증 Principal로 변환한다")
    void authenticate_ValidAccessToken_ReturnsAuthenticationWithTokenCredentials() {
        // given
        String token = "access-token";
        Claims claims = claims("1", "access");
        when(jwtUtil.getClaims(token)).thenReturn(claims);
        when(redisUtil.isBlackList(token)).thenReturn(false);
        when(customUserDetailsService.loadUserByUsername("1")).thenReturn(userDetails(1L, true));

        // when
        Authentication result = authenticator().authenticate(token);

        // then
        assertEquals("access-token", result.getCredentials());
        assertEquals(1L, ((CustomUserDetails) result.getPrincipal()).getMemberId());
    }

    @Test
    @DisplayName("유효한 ROLE_GUEST access token도 STOMP principal로 인증한다")
    void authenticate_ActiveGuest_ReturnsGuestAuthority() {
        // given
        String token = "guest-access-token";
        Claims claims = claims("42", "access");
        when(jwtUtil.getClaims(token)).thenReturn(claims);
        when(redisUtil.isBlackList(token)).thenReturn(false);
        when(customUserDetailsService.loadUserByUsername("42"))
                .thenReturn(userDetails(42L, true, Role.ROLE_GUEST));

        // when
        Authentication result = authenticator().authenticate(token);

        // then
        assertEquals(42L, ((CustomUserDetails) result.getPrincipal()).getMemberId());
        assertEquals("ROLE_GUEST", result.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    @DisplayName("만료되거나 손상된 token을 유효하지 않은 token으로 거부한다")
    void authenticate_ExpiredOrMalformedToken_ThrowsInvalidToken() {
        // given
        String token = "expired-token";
        when(jwtUtil.getClaims(token)).thenThrow(new IllegalArgumentException("expired"));

        // when
        AuthException exception = assertThrows(
                AuthException.class,
                () -> authenticator().authenticate(token)
        );

        // then
        assertEquals(AuthErrorCode.TOKEN_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("blacklist에 등록된 token을 거부한다")
    void authenticate_BlacklistedToken_ThrowsBlacklistError() {
        // given
        String token = "blacklisted-token";
        Claims claims = mock(Claims.class);
        when(claims.get("category", String.class)).thenReturn("access");
        when(jwtUtil.getClaims(token)).thenReturn(claims);
        when(redisUtil.isBlackList(token)).thenReturn(true);

        // when
        AuthException exception = assertThrows(
                AuthException.class,
                () -> authenticator().authenticate(token)
        );

        // then
        assertEquals(AuthErrorCode.TOKEN_BLACKLIST, exception.getCode());
    }

    private StompAccessTokenAuthenticator authenticator() {
        return new StompAccessTokenAuthenticator(jwtUtil, redisUtil, customUserDetailsService);
    }

    private Claims claims(String subject, String category) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(subject);
        when(claims.get("category", String.class)).thenReturn(category);
        return claims;
    }

    private CustomUserDetails userDetails(Long memberId, boolean active) {
        return userDetails(memberId, active, Role.ROLE_USER);
    }

    private CustomUserDetails userDetails(Long memberId, boolean active, Role role) {
        return new CustomUserDetails(
                MemberResDTO.MemberResultDTO.builder()
                        .id(memberId)
                        .role(role)
                        .isActive(active)
                        .build()
        );
    }
}
