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
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.member.service.command.MemberCommandService;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.global.util.JwtUtil;
import com.infragen.infragen.global.util.RedisUtil;
import io.jsonwebtoken.Claims;
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
    private MemberRepository memberRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private KakaoOAuthClient kakaoOAuthClient;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("일반 회원가입 - 성공 시 토큰 반환 검증")
    void signup_Success() {
        // given
        AuthReqDTO.SignupDTO request = AuthReqDTO.SignupDTO.builder()
                .email("test@test.com").password("password").nickname("Tester").build();
        MemberResDTO.MemberResultDTO memberDTO = MemberResDTO.MemberResultDTO.builder()
                .id(1L).email("test@test.com").role(Role.ROLE_USER).build();

        when(memberCommandService.createMember(any())).thenReturn(memberDTO);
        when(jwtUtil.createAccessToken(anyLong(), any())).thenReturn("access_token");
        when(jwtUtil.createRefreshToken(anyLong())).thenReturn("refresh_token");
        when(jwtUtil.getExpirationTime(anyString())).thenReturn(1000L);

        // when
        AuthResDTO.TokenResultDTO result = authService.signup(request);

        // then
        assertNotNull(result);
        assertEquals("access_token", result.getAccessToken());
        verify(redisUtil).set(eq("RT:1"), eq("refresh_token"), any());
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
        when(jwtUtil.createAccessToken(anyLong(), any())).thenReturn("access_token");
        when(jwtUtil.createRefreshToken(anyLong())).thenReturn("refresh_token");
        when(jwtUtil.getExpirationTime(anyString())).thenReturn(1000L);

        // when
        AuthResDTO.TokenResultDTO result = authService.login(request);

        // then
        assertNotNull(result);
        assertEquals("access_token", result.getAccessToken());
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
        when(jwtUtil.createAccessToken(anyLong(), any())).thenReturn("app_access");
        when(jwtUtil.createRefreshToken(anyLong())).thenReturn("app_refresh_token");
        when(jwtUtil.getExpirationTime(anyString())).thenReturn(10000L);

        AuthResDTO.TokenResultDTO result = authService.socialLogin(provider, request);

        // then
        assertNotNull(result);
        assertEquals("app_access", result.getAccessToken());
        verify(memberCommandService).createSocialMember(any(), any(), eq("12345"), eq(SocialProvider.KAKAO));
        verify(redisUtil).set(eq("RT:1"), eq("app_refresh_token"), any());
    }

    @Test
    @DisplayName("로그아웃 - 만료된 토큰 로그아웃 성공 검증")
    void logout_ExpiredToken_Success() {
        // given
        String accessToken = "Bearer expired_token";
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1");

        when(jwtUtil.getClaimsForLogout(anyString())).thenReturn(claims);
        when(redisUtil.hasKey("RT:1")).thenReturn(true);
        when(jwtUtil.getExpirationTime(anyString())).thenReturn(0L);

        // when
        authService.logout(accessToken);

        // then
        verify(redisUtil).delete("RT:1");
        verify(redisUtil).setBlackList(anyString(), eq(0L));
    }

    @Test
    @DisplayName("RTR 동시성 방어 - 동일 RT 재발급 2회 요청 시 실패 검증")
    void reissueToken_Concurrency_Fail() {
        // given
        String refreshToken = "Bearer valid_refresh";
        Claims claims = mock(Claims.class);

        // when
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("category", String.class)).thenReturn("refresh");

        when(jwtUtil.validateToken(anyString())).thenReturn(true);
        when(jwtUtil.getClaims(anyString())).thenReturn(claims);

        when(redisUtil.getAndDelete("RT:1"))
                .thenReturn("valid_refresh")
                .thenReturn(null);

        Member member = Member.builder().role(Role.ROLE_USER).build();
        ReflectionTestUtils.setField(member, "id", 1L);

        when(memberQueryService.findById(1L)).thenReturn(member);
        when(jwtUtil.createAccessToken(anyLong(), any())).thenReturn("new_access");
        when(jwtUtil.createRefreshToken(anyLong())).thenReturn("new_refresh");
        when(jwtUtil.getExpirationTime(anyString())).thenReturn(10000L);

        // then
        assertNotNull(authService.reissueToken(refreshToken));
        assertThrows(AuthException.class, () -> authService.reissueToken(refreshToken));
    }
}
