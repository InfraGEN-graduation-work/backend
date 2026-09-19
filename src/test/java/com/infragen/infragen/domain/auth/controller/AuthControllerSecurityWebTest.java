package com.infragen.infragen.domain.auth.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.infragen.infragen.domain.auth.dto.response.AuthResDTO;
import com.infragen.infragen.domain.auth.service.AuthService;
import com.infragen.infragen.domain.auth.service.EmailVerificationService;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.service.command.ProjectCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;
import com.infragen.infragen.global.apiPayload.handler.GeneralExceptionAdvice;
import com.infragen.infragen.global.auth.AuthenticationEntryPointImpl;
import com.infragen.infragen.global.auth.CustomUserDetailsService;
import com.infragen.infragen.global.auth.RefreshTokenCookieWriter;
import com.infragen.infragen.global.auth.filter.JwtAuthFilter;
import com.infragen.infragen.global.auth.filter.JwtExceptionFilter;
import com.infragen.infragen.global.config.SecurityConfig;
import com.infragen.infragen.global.properties.JwtProperties;
import com.infragen.infragen.global.util.JwtUtil;
import com.infragen.infragen.global.util.RedisUtil;

@WebMvcTest(controllers = {AuthController.class, com.infragen.infragen.domain.project.controller.ProjectController.class}, properties = {
        "cors.allowed-origins=http://localhost",
        "jwt.secret=guest-security-test-secret-guest-security-test-secret",
        "jwt.access-token.expiration-time=60000",
        "jwt.refresh-token.expiration-time=120000",
        "jwt.dev-token.expiration-time=60000"
})
@ContextConfiguration(classes = AuthControllerSecurityWebTest.Config.class)
@DisplayName("Guest 인증 Security Web 테스트")
class AuthControllerSecurityWebTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(JwtProperties.class)
    @Import({
            AuthController.class,
            com.infragen.infragen.domain.project.controller.ProjectController.class,
            GeneralExceptionAdvice.class,
            RefreshTokenCookieWriter.class,
            SecurityConfig.class,
            JwtUtil.class,
            JwtAuthFilter.class,
            JwtExceptionFilter.class,
            AuthenticationEntryPointImpl.class,
            CustomUserDetailsService.class
    })
    static class Config {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private RedisUtil redisUtil;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private ProjectCommandService projectCommandService;

    @MockitoBean
    private ProjectQueryService projectQueryService;

    @Test
    @DisplayName("permitAll guest endpoint에서 받은 ROLE_GUEST token으로 보호 API 인증")
    void guestLogin_TokenAuthenticatesAgainstProtectedApi() throws Exception {
        // given
        Member guestMember = member(42L, Role.ROLE_GUEST, true);
        when(memberRepository.findById(42L)).thenReturn(Optional.of(guestMember));
        when(redisUtil.isBlackList(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(projectQueryService.getProjects(42L)).thenReturn(
                ProjectResDTO.ProjectPreviewListResDTO.builder().projectList(List.of()).build()
        );
        when(authService.guestLogin()).thenReturn(new AuthResDTO.TokenResultDTO(
                jwtUtil.createAccessToken(42L, Role.ROLE_GUEST),
                "guest-refresh-token"
        ));

        // when
        var loginResponse = mockMvc.perform(post("/api/v1/auth/guest"));
        String accessToken = loginResponse.andReturn().getResponse()
                .getContentAsString().split("\\\"accessToken\\\":\\\"")[1].split("\\\"")[0];
        var protectedResponse = mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer " + accessToken));

        // then
        loginResponse
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH200_2"));
        protectedResponse
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJECT200_1"));
        verify(authService).guestLogin();
        verify(projectQueryService).getProjects(42L);
    }

    @Test
    @DisplayName("guest 발급 endpoint 외 보호 API는 비로그인 요청을 거부")
    void protectedApi_AnonymousRequest_ReturnsUnauthorized() throws Exception {
        // when
        var response = mockMvc.perform(get("/api/v1/projects"));

        // then
        response.andExpect(status().isUnauthorized());
        verifyNoInteractions(projectQueryService, projectCommandService);
    }

    @Test
    @DisplayName("비활성 guest의 기존 access token은 인증에 사용할 수 없음")
    void protectedApi_InactiveGuestToken_ReturnsUnauthorized() throws Exception {
        // given
        Member inactiveGuest = member(43L, Role.ROLE_GUEST, false);
        when(memberRepository.findById(43L)).thenReturn(Optional.of(inactiveGuest));
        when(redisUtil.isBlackList(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        String token = jwtUtil.createAccessToken(43L, Role.ROLE_GUEST);

        // when
        var response = mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer " + token));

        // then
        response.andExpect(status().isUnauthorized());
        verifyNoInteractions(projectQueryService);
    }

    private Member member(Long memberId, Role role, boolean active) {
        Member member = Member.builder()
                .email("guest-" + memberId + "@guest.infragen.local")
                .password("not-used")
                .nickname("Guest " + memberId)
                .role(role)
                .isActive(active)
                .build();
        ReflectionTestUtils.setField(member, "id", memberId);
        return member;
    }
}
