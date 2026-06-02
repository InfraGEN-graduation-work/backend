package com.infragen.infragen.domain.auth.service;

import com.infragen.infragen.domain.auth.client.KakaoOAuthClient;
import com.infragen.infragen.domain.auth.client.OAuth2UserInfo;
import com.infragen.infragen.domain.auth.dto.request.AuthReqDTO;
import com.infragen.infragen.domain.auth.dto.response.AuthResDTO;
import com.infragen.infragen.domain.auth.dto.response.KakaoTokenResDTO;
import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.enums.SocialProvider;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.MemberErrorCode;
import com.infragen.infragen.domain.member.service.command.MemberCommandService;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.global.util.JwtUtil;
import com.infragen.infragen.global.util.RedisUtil;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final String REDIS_RT_PREFIX = "RT:";
    private static final String BEARER_PREFIX_LOWER = "bearer ";

    private final MemberQueryService memberQueryService;
    private final MemberCommandService memberCommandService;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final PasswordEncoder passwordEncoder;
    private final KakaoOAuthClient kakaoOAuthClient;

    // 일반 회원가입
    public AuthResDTO.TokenResultDTO signup(AuthReqDTO.SignupDTO request) {
        MemberResDTO.MemberResultDTO memberDTO = memberCommandService.createMember(request);
        return generateAndSaveTokens(memberDTO.id(), memberDTO.role());
    }

    // 일반 로그인
    public AuthResDTO.TokenResultDTO login(AuthReqDTO.LoginDTO request) {
        // 이메일이 틀렸거나 비밀번호가 틀려도 같은 응답을 내게끔
        try {
            Member member = memberQueryService.findByEmail(request.getEmail());

            if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
                throw new AuthException(AuthErrorCode.UNMATCHED_EMAIL_OR_PASSWORD);
            }

            return generateAndSaveTokens(member.getId(), member.getRole());
        } catch (MemberException e) {
            if (e.getCode() == MemberErrorCode.MEMBER_NOT_FOUND) {
                throw new AuthException(AuthErrorCode.UNMATCHED_EMAIL_OR_PASSWORD);
            }
            throw e;
        }
    }

    // 소셜 로그인
    public AuthResDTO.TokenResultDTO socialLogin(String provider, AuthReqDTO.SocialLoginDTO request) {
        SocialProvider socialProvider = SocialProvider.fromString(provider);
        OAuth2UserInfo userInfo = fetchUserInfo(socialProvider, request.getAuthorizationCode());

        String socialId = userInfo.getSocialId();
        String email = userInfo.getEmail();
        String nickname = userInfo.getNickname();

        // Query와 Command 모두 MemberResultDTO를 반환하므로 타입이 일치함
        MemberResDTO.MemberResultDTO memberDTO = memberQueryService.findBySocialIdAndProvider(socialId, socialProvider)
                .orElseGet(() -> memberCommandService.createSocialMember(email, nickname, socialId, socialProvider));

        return generateAndSaveTokens(memberDTO.id(), memberDTO.role());
    }

    // 로그아웃
    public void logout(String accessToken) {
        String resolvedToken = resolveToken(accessToken);
        Long memberId;

        try {
            String subject = jwtUtil.getClaimsForLogout(resolvedToken).getSubject();
            if (subject == null || subject.trim().isEmpty()) {
                throw new IllegalArgumentException("Subject is empty");
            }
            memberId = Long.parseLong(subject);
        } catch (Exception e) {
            log.warn("로그아웃 실패: 유효하지 않은 토큰. error={}", e.getMessage());
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }

        String redisKey = REDIS_RT_PREFIX + memberId;
        
        if (redisUtil.hasKey(redisKey)) {
            redisUtil.delete(redisKey);
        }

        Long expirationMillis = jwtUtil.getExpirationTime(resolvedToken);
        redisUtil.setBlackList(resolvedToken, expirationMillis);

        log.info("사용자 로그아웃 완료: memberId={}", memberId);
    }

    // 토큰 재발급
    public AuthResDTO.TokenResultDTO reissueToken(String refreshToken) {
        String resolvedToken = resolveToken(refreshToken);

        if (!jwtUtil.validateToken(resolvedToken)) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }

        String category = jwtUtil.getClaims(resolvedToken).get("category", String.class);
        if (!"refresh".equals(category)) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }

        Long memberId = extractMemberId(resolvedToken);
        String redisKey = REDIS_RT_PREFIX + memberId;

        String storedRefreshToken = (String) redisUtil.getAndDelete(redisKey);
        if (storedRefreshToken == null || !storedRefreshToken.equals(resolvedToken)) {
            log.warn("토큰 재발급 실패: 탈취 의심 / memberId={}", memberId);
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }

        Member member = memberQueryService.findById(memberId);

        return generateAndSaveTokens(member.getId(), member.getRole());
    }

    private OAuth2UserInfo fetchUserInfo(SocialProvider provider, String code) {
        return switch (provider) {
            case KAKAO -> {
                KakaoTokenResDTO tokenResponse = kakaoOAuthClient.fetchKakaoAccessToken(code);
                yield kakaoOAuthClient.fetchKakaoUserInfo(tokenResponse.accessToken());
            }
            default -> throw new AuthException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        };
    }

    private AuthResDTO.TokenResultDTO generateAndSaveTokens(Long memberId, Role role) {
        String accessToken = jwtUtil.createAccessToken(memberId, role);
        String refreshToken = jwtUtil.createRefreshToken(memberId);

        Long expirationMillis = jwtUtil.getExpirationTime(refreshToken);
        redisUtil.set(REDIS_RT_PREFIX + memberId, refreshToken, Duration.ofMillis(expirationMillis));

        return new AuthResDTO.TokenResultDTO(accessToken, refreshToken);
    }

    private String resolveToken(String token) {
        if (token != null) {
            String trimmedToken = token.trim();
            if (trimmedToken.toLowerCase().startsWith(BEARER_PREFIX_LOWER)) {
                return trimmedToken.substring(7).trim();
            }
        }
        return token;
    }

    private Long extractMemberId(String token) {
        String subject = jwtUtil.getClaims(token).getSubject();
        if (subject == null || subject.trim().isEmpty()) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }
    }
}
