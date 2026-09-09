package com.infragen.infragen.domain.auth.service;

import com.infragen.infragen.domain.auth.client.KakaoOAuthClient;
import com.infragen.infragen.domain.auth.dto.request.AuthReqDTO;
import com.infragen.infragen.domain.auth.dto.response.AuthResDTO;
import com.infragen.infragen.domain.auth.dto.response.KakaoTokenResDTO;
import com.infragen.infragen.domain.auth.dto.response.KakaoUserInfoDTO;
import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.enums.SocialProvider;
import com.infragen.infragen.domain.member.service.command.MemberCommandService;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private MemberQueryService memberQueryService;
    @Mock
    private MemberCommandService memberCommandService;
    @Mock
    private KakaoOAuthClient kakaoOAuthClient;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("일반 회원가입 - 성공 시 회원만 생성하고 토큰은 발급하지 않음")
    void signup_Success() {
        // given
        AuthReqDTO.SignupDTO request = AuthReqDTO.SignupDTO.builder()
                .email("test@test.com").password("password").nickname("Tester").build();
        MemberResDTO.MemberResultDTO memberDTO = MemberResDTO.MemberResultDTO.builder()
                .id(1L).email("test@test.com").role(Role.ROLE_USER).build();

        when(memberCommandService.createMember(any())).thenReturn(memberDTO);

        // when
        authService.signup(request);

        // then
        verify(memberCommandService).createMember(request);
        verify(tokenService, never()).issueTokens(anyLong(), any());
    }

    @Test
    @DisplayName("일반 회원가입 - 이메일 중복 시 예외 발생 검증")
    void signup_Fail_DuplicateEmail() {
        // given
        AuthReqDTO.SignupDTO request = AuthReqDTO.SignupDTO.builder()
                .email("duplicate@test.com")
                .password("password")
                .nickname("Tester")
                .build();

        when(memberCommandService.createMember(any()))
                .thenThrow(new MemberException(MemberErrorCode.DUPLICATE_EMAIL));

        // when & then
        assertThrows(MemberException.class, 
                () -> authService.signup(request));
        
        verify(tokenService, never()).issueTokens(anyLong(), any());
    }

    @Test
    @DisplayName("일반 로그인 - 성공 시 토큰 반환 검증")
    void login_Success() {
        // given
        AuthReqDTO.LoginDTO request = AuthReqDTO.LoginDTO.builder()
                .email("test@test.com").password("password").build();
        Member member = Member.builder()
                .email("test@test.com").password("encoded_pw").role(Role.ROLE_USER).build();
        ReflectionTestUtils.setField(member, "id", 1L);

        when(memberQueryService.findByEmail(anyString())).thenReturn(member);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        AuthResDTO.TokenResultDTO tokens = new AuthResDTO.TokenResultDTO(
                "access_token",
                "refresh_token"
        );
        when(tokenService.issueTokens(1L, Role.ROLE_USER)).thenReturn(tokens);

        // when
        AuthResDTO.TokenResultDTO result = authService.login(request);

        // then
        assertNotNull(result);
        assertEquals("access_token", result.getAccessToken());
        verify(tokenService).issueTokens(1L, Role.ROLE_USER);
    }

    @Test
    @DisplayName("소셜 로그인 - 신규 가입 시나리오 성공 검증")
    void socialLogin_NewMember_Success() {
        // given
        String provider = "kakao";
        AuthReqDTO.SocialLoginDTO request = AuthReqDTO.SocialLoginDTO.builder()
                .authorizationCode("valid_code").build();
        KakaoTokenResDTO tokenResponse = KakaoTokenResDTO.builder()
                .accessToken("k_access").build();
        KakaoUserInfoDTO userInfo = KakaoUserInfoDTO.builder().id(12345L).build();
        MemberResDTO.MemberResultDTO memberDTO = MemberResDTO.MemberResultDTO.builder()
                .id(1L).email("kakao@test.com").role(Role.ROLE_USER).build();

        // when
        when(kakaoOAuthClient.fetchKakaoAccessToken(anyString())).thenReturn(tokenResponse);
        when(kakaoOAuthClient.fetchKakaoUserInfo(anyString())).thenReturn(userInfo);
        when(memberQueryService.findBySocialIdAndProvider(anyString(), any())).thenReturn(Optional.empty());
        when(memberCommandService.createSocialMember(any(), any(), anyString(), any())).thenReturn(memberDTO);
        AuthResDTO.TokenResultDTO tokens = new AuthResDTO.TokenResultDTO(
                "app_access",
                "app_refresh_token"
        );
        when(tokenService.issueTokens(1L, Role.ROLE_USER)).thenReturn(tokens);

        AuthResDTO.TokenResultDTO result = authService.socialLogin(provider, request);

        // then
        assertNotNull(result);
        assertEquals("app_access", result.getAccessToken());
        verify(memberCommandService).createSocialMember(any(), any(), eq("12345"), eq(SocialProvider.KAKAO));
        verify(tokenService).issueTokens(1L, Role.ROLE_USER);
    }

    @Test
    @DisplayName("로그아웃 - 토큰 처리를 TokenService에 위임")
    void logout_ValidToken_DelegatesToTokenService() {
        // given
        String accessToken = "Bearer access_token";
        when(tokenService.resolveToken(accessToken)).thenReturn("access_token");
        when(tokenService.extractMemberIdForLogout("access_token")).thenReturn(1L);

        // when
        authService.logout(accessToken);

        // then
        verify(tokenService).resolveToken(accessToken);
        verify(tokenService).extractMemberIdForLogout("access_token");
        verify(tokenService).deleteRefreshToken(1L);
        verify(tokenService).blacklistAccessToken("access_token");
    }

    @Test
    @DisplayName("RTR 동시성 방어 - 동일 RT 재발급 2회 요청 시 실패 검증")
    void reissueToken_Concurrency_Fail() {
        // given
        String refreshToken = "Bearer valid_refresh";
        when(tokenService.consumeRefreshToken(refreshToken))
                .thenReturn(1L)
                .thenThrow(new AuthException(com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode.TOKEN_INVALID));

        Member member = Member.builder().role(Role.ROLE_USER).build();
        ReflectionTestUtils.setField(member, "id", 1L);

        when(memberQueryService.findById(1L)).thenReturn(member);
        when(tokenService.issueTokens(1L, Role.ROLE_USER))
                .thenReturn(new AuthResDTO.TokenResultDTO("new_access", "new_refresh"));

        // when
        AuthResDTO.TokenResultDTO result = authService.reissueToken(refreshToken);

        // then
        assertNotNull(result);
        assertEquals("new_access", result.getAccessToken());
        verify(tokenService).consumeRefreshToken(refreshToken);

        assertThrows(AuthException.class, () -> authService.reissueToken(refreshToken));
    }
}
