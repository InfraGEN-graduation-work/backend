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

    /** guest member를 생성하고 기존 access·refresh token 발급 흐름을 사용한다. */
    public AuthResDTO.TokenResultDTO guestLogin() {
        MemberResDTO.MemberResultDTO guestMember = memberCommandService.createGuestMember();

        return tokenService.issueTokens(guestMember.id(), guestMember.role());
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

    /**
     * 일반 회원의 refresh token을 삭제하고 access token을 blacklist에 등록한다.
     * guest는 로그아웃하면 소유 프로젝트가 정리되지 않은 채 남으므로 거부하고, 이용 종료({@code DELETE /api/v1/members/me})를 쓰게 한다.
     * 인증된 요청에서만 호출되므로 token subject의 회원은 활성 상태다.
     *
     * @throws MemberException guest면 GUEST_LOGOUT_NOT_ALLOWED. 이때 토큰은 변경하지 않는다
     */
    public void logout(String accessToken) {
        String resolvedToken = tokenService.resolveToken(accessToken);
        Long memberId = tokenService.extractMemberIdForLogout(resolvedToken);

        // 토큰을 지우기 전에 거부해야 guest가 로그인 상태를 유지한 채 이용 종료로 이어갈 수 있다.
        if (memberQueryService.findById(memberId).getRole() == Role.ROLE_GUEST) {
            throw new MemberException(MemberErrorCode.GUEST_LOGOUT_NOT_ALLOWED);
        }

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
