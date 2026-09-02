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
import com.infragen.infragen.domain.member.enums.SocialProvider;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.service.command.MemberCommandService;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final MemberQueryService memberQueryService;
    private final MemberCommandService memberCommandService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final KakaoOAuthClient kakaoOAuthClient;

    // 일반 회원가입 (인증 토큰·응답 본문 없음 — 로그인에서 별도 처리)
    public void signup(AuthReqDTO.SignupDTO request) {
        memberCommandService.createMember(request);
    }

    // 일반 로그인
    public AuthResDTO.TokenResultDTO login(AuthReqDTO.LoginDTO request) {
        // 이메일이 틀렸거나 비밀번호가 틀려도 같은 응답을 내게끔
        try {
            Member member = memberQueryService.findByEmail(request.getEmail());

            if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
                throw new AuthException(AuthErrorCode.UNMATCHED_EMAIL_OR_PASSWORD);
            }

            return tokenService.issueTokens(member.getId(), member.getRole());
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

        return tokenService.issueTokens(memberDTO.id(), memberDTO.role());
    }

    // 로그아웃
    public void logout(String accessToken) {
        String resolvedToken = tokenService.resolveToken(accessToken);
        Long memberId = tokenService.extractMemberIdForLogout(resolvedToken);

        tokenService.deleteRefreshToken(memberId);
        tokenService.blacklistAccessToken(resolvedToken);

        log.info("사용자 로그아웃 완료: memberId={}", memberId);
    }

    // 토큰 재발급
    public AuthResDTO.TokenResultDTO reissueToken(String refreshToken) {
        Long memberId = tokenService.consumeRefreshToken(refreshToken);

        Member member = memberQueryService.findById(memberId);

        return tokenService.issueTokens(member.getId(), member.getRole());
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

}
