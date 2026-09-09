package com.infragen.infragen.global.util;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.ExpiredJwtException;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.global.properties.JwtProperties;

@Slf4j
@Component
public class JwtUtil {
    private final SecretKey secretKey;
    private final Duration accessExpiration;
    private final Duration refreshExpiration;
    private final Duration devExpiration;

    public JwtUtil(JwtProperties jwtProperties) {
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessExpiration = Duration.ofMillis(jwtProperties.getAccessToken().getExpirationTime());
        this.refreshExpiration = Duration.ofMillis(jwtProperties.getRefreshToken().getExpirationTime());
        this.devExpiration = Duration.ofMillis(jwtProperties.getDevToken().getExpirationTime());
    }

    // 토큰 생성
    private String createToken(Long memberId, Duration expiration, Role role, String category) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + expiration.toMillis());
        return Jwts.builder()
                .subject(memberId.toString())
                .issuedAt(now)
                .expiration(validity)
                .signWith(secretKey)
                .claim("role", role)
                .claim("category", category)
                .compact();
    }

    // 토큰의 유효성 검증
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            log.warn("Invalid token: signature or format validation failed");
            return false;
        }
    }

    // 유효 시간 추출
    public Long getExpirationTime(String token) {
        try {
            Claims claims = getClaims(token);
            Date expiration = claims.getExpiration();
            long now = new Date().getTime();

            // 현재 시간과의 차이를 반환
            return expiration.getTime() - now;
        } catch (Exception e) {
            return 0L;
        }
    }

    // jwt 파싱 및 클레임 추출
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 로그아웃 전용: 토큰이 만료되었더라도 파싱하여 Claims를 강제로 반환
     * 단, 서명(Signature)이 다르거나 손상된 토큰이면 예외를 던짐
     */
    public Claims getClaimsForLogout(String token) {
        try {
            return getClaims(token);
        } catch (ExpiredJwtException e) {
            // 토큰이 만료되었을 뿐, 서명은 유효하므로 안에 들어있는 Claims를 꺼내서 반환
            return e.getClaims();
        } catch (Exception e) {
            // 서명이 조작되었거나 형식이 깨진 경우는 로그아웃조차 시켜주지 않음
            log.warn("Invalid token for logout: signature or format validation failed");
            throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
        }
    }

    // 액세스 토큰 생성
    public String createAccessToken(Long memberId, Role role) {
        return createToken(memberId, accessExpiration, role, "access");
    }

    // 리프레쉬 토큰 생성
    public String createRefreshToken(Long memberId) {
        return createToken(memberId, refreshExpiration, Role.ROLE_USER, "refresh");
    }

    // 개발자 전용 임시 토큰 생성
    public String createDevToken(Long memberId) {
        return createToken(memberId, devExpiration, Role.ROLE_USER, "access");
    }
}
