package com.infragen.infragen.global.auth.filter;

import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.AuthErrorCode;
import com.infragen.infragen.global.auth.CustomUserDetailsService;
import com.infragen.infragen.global.util.JwtUtil;
import com.infragen.infragen.global.util.RedisUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;
    private final RedisUtil redisUtil;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        // Authorization 헤더에서 토큰 추출
        String authHeader = request.getHeader("Authorization");
        String token = null;
        Long memberId = null;

        // Bearer로 시작하는 토큰이 있는지 확인
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);

            Claims claims = jwtUtil.getClaimsFromToken(token);

            String category = claims.get("category", String.class);

            if (category == null || category.equals("refresh")) {
                logger.warn("access token이 아닌 토큰으로 인증을 시도하셨습니다.");
                throw new AuthException(AuthErrorCode.TOKEN_INVALID);
            }

            if (redisUtil.isBlackList(token)) {
                logger.warn("블랙리스트에 포함된 토큰입니다.");
                throw new AuthException(AuthErrorCode.TOKEN_BLACKLIST);
            }

            memberId = Long.parseLong(claims.getSubject());
        }

        // 토큰이 유효하고, SecurityContext에 인증 정보가 없는 경우에 인증 처리
        if (memberId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // 사용자 정보 조회
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(memberId.toString());

            // 조회된 사용자 정보와 토큰의 유효성을 확인
            if (jwtUtil.validateToken(token)) {
                // Spring Security가 사용할 인증 토큰 생성
                Authentication auth = new UsernamePasswordAuthenticationToken(
                        userDetails, // 사용자 정보
                        null, // 비밀번호(사용X)
                        userDetails.getAuthorities() // 권한 목록
                );

                // 인증 완료 후 SecurityContextHolder에 넣기
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        // 다음 필터로 요청 전달
        filterChain.doFilter(request, response);
    }
}
