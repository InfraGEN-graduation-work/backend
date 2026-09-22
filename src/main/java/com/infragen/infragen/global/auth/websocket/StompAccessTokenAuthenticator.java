package com.infragen.infragen.global.auth.websocket;

import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import com.infragen.infragen.global.auth.CustomUserDetails;
import com.infragen.infragen.global.auth.CustomUserDetailsService;
import com.infragen.infragen.global.util.JwtUtil;
import com.infragen.infragen.global.util.RedisUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompAccessTokenAuthenticator {
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final CustomUserDetailsService customUserDetailsService;

    /**
     * access token의 서명·만료·blacklist·active member를 확인하고 STOMP Principal을 생성한다.
     */
    public Authentication authenticate(String token) {
        Claims claims = parseClaims(token);
        String category = claims.get("category", String.class);
        if (!JwtUtil.ACCESS_TOKEN_CATEGORY.equals(category) || redisUtil.isBlackList(token)) {
            throw new AuthException(
                    JwtUtil.ACCESS_TOKEN_CATEGORY.equals(category)
                            ? AuthErrorCode.TOKEN_BLACKLIST
                            : AuthErrorCode.TOKEN_INVALID
            );
        }

        String memberId = claims.getSubject();
        if (memberId == null || memberId.isBlank()) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }

        CustomUserDetails userDetails;
        try {
            userDetails = (CustomUserDetails)
                    customUserDetailsService.loadUserByUsername(memberId);
        } catch (UsernameNotFoundException | NumberFormatException exception) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }
        if (!userDetails.isEnabled()) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }

        return new UsernamePasswordAuthenticationToken(
                userDetails,
                token,
                userDetails.getAuthorities()
        );
    }

    private Claims parseClaims(String token) {
        try {
            return jwtUtil.getClaims(token);
        } catch (RuntimeException exception) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }
    }
}
