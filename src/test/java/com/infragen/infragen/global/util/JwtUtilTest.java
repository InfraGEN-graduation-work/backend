package com.infragen.infragen.global.util;

import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.global.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {
    private static final String SECRET = "jwt-util-test-secret-jwt-util-test-secret-1234567890";
    private static final String ISSUER = "infra-gen";

    @Test
    @DisplayName("access token에 issuer와 access category를 포함한다")
    void createAccessToken_IncludesIssuerAndCategory() {
        // given
        JwtUtil jwtUtil = jwtUtil();

        // when
        Claims claims = jwtUtil.getClaims(jwtUtil.createAccessToken(1L, Role.ROLE_USER));

        // then
        assertEquals(ISSUER, claims.getIssuer());
        assertEquals(JwtUtil.ACCESS_TOKEN_CATEGORY, claims.get("category", String.class));
    }

    @Test
    @DisplayName("issuer가 다른 token을 거부한다")
    void getClaims_WrongIssuer_ThrowsInvalidToken() {
        // given
        JwtUtil jwtUtil = jwtUtil();
        String token = signedToken("another-service", "1", JwtUtil.ACCESS_TOKEN_CATEGORY);

        // when
        AuthException exception = assertThrows(AuthException.class, () -> jwtUtil.getClaims(token));

        // then
        assertEquals("AUTH401_1", exception.getCode().getCode());
    }

    @Test
    @DisplayName("issuer가 없는 token을 거부한다")
    void getClaims_MissingIssuer_ThrowsInvalidToken() {
        // given
        JwtUtil jwtUtil = jwtUtil();
        Date now = new Date();
        String token = Jwts.builder()
                .subject("1")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000L))
                .claim("category", JwtUtil.ACCESS_TOKEN_CATEGORY)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        // when
        AuthException exception = assertThrows(AuthException.class, () -> jwtUtil.getClaims(token));

        // then
        assertEquals("AUTH401_1", exception.getCode().getCode());
    }

    @Test
    @DisplayName("숫자가 아닌 subject를 가진 token을 거부한다")
    void getClaims_NonNumericSubject_ThrowsInvalidToken() {
        // given
        JwtUtil jwtUtil = jwtUtil();
        String token = signedToken(ISSUER, "member-1", JwtUtil.ACCESS_TOKEN_CATEGORY);

        // when
        AuthException exception = assertThrows(AuthException.class, () -> jwtUtil.getClaims(token));

        // then
        assertEquals("AUTH401_1", exception.getCode().getCode());
    }

    private JwtUtil jwtUtil() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer(ISSUER);

        JwtProperties.AccessToken accessToken = new JwtProperties.AccessToken();
        accessToken.setExpirationTime(3_600_000L);
        properties.setAccessToken(accessToken);

        JwtProperties.RefreshToken refreshToken = new JwtProperties.RefreshToken();
        refreshToken.setExpirationTime(3_600_000L);
        properties.setRefreshToken(refreshToken);

        JwtProperties.DevToken devToken = new JwtProperties.DevToken();
        devToken.setExpirationTime(3_600_000L);
        properties.setDevToken(devToken);
        return new JwtUtil(properties);
    }

    private String signedToken(String issuer, String subject, String category) {
        Date now = new Date();
        return Jwts.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000L))
                .claim("category", category)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
