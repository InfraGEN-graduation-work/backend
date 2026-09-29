package com.infragen.infragen.domain.member.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

import com.infragen.infragen.domain.auth.service.AuthService;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.enums.WithdrawalProjectOutcome;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.member.service.command.MemberCommandService;
import com.infragen.infragen.domain.member.service.command.MemberWithdrawalCommandService;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.member.service.query.MemberWithdrawalQueryService;
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

/** 실제 JWT filter chain에서 탈퇴 안내 조회와 탈퇴 API의 인증·쿼리 파라미터 바인딩·오류 응답을 확인한다. */
@WebMvcTest(controllers = MemberController.class, properties = {
        "cors.allowed-origins=http://localhost",
        "jwt.secret=issue71-test-secret-issue71-test-secret-issue71-test-secret",
        "jwt.issuer=infra-gen",
        "jwt.access-token.expiration-time=60000",
        "jwt.refresh-token.expiration-time=120000",
        "jwt.dev-token.expiration-time=60000"
})
@ContextConfiguration(classes = MemberControllerWithdrawalSecurityWebTest.Config.class)
class MemberControllerWithdrawalSecurityWebTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(JwtProperties.class)
    @Import({MemberController.class, GeneralExceptionAdvice.class, SecurityConfig.class,
            JwtUtil.class, JwtAuthFilter.class, JwtExceptionFilter.class,
            AuthenticationEntryPointImpl.class, CustomUserDetailsService.class,
            RefreshTokenCookieWriter.class})
    static class Config {
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtUtil jwtUtil;
    @MockitoBean RedisUtil redisUtil;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean MemberQueryService memberQueryService;
    @MockitoBean MemberCommandService memberCommandService;
    @MockitoBean MemberWithdrawalQueryService withdrawalQueryService;
    @MockitoBean MemberWithdrawalCommandService withdrawalCommandService;
    @MockitoBean AuthService authService;

    @Test
    @DisplayName("탈퇴와 탈퇴 안내 조회는 인증이 없으면 401로 거부한다")
    void withdrawalEndpoints_Anonymous_Unauthorized() throws Exception {
        // given
        var deleteRequest = delete("/api/v1/members/me").param("confirmedDeletionProjectIds", "2");
        var previewRequest = get("/api/v1/members/me/withdrawal-preview");

        // when
        var deleteResponse = mockMvc.perform(deleteRequest);
        var previewResponse = mockMvc.perform(previewRequest);

        // then
        deleteResponse.andExpect(status().isUnauthorized());
        previewResponse.andExpect(status().isUnauthorized());
        verifyNoInteractions(withdrawalCommandService, withdrawalQueryService);
    }

    @Test
    @DisplayName("탈퇴는 쉼표로 구분한 확인 ID를 집합으로 전달하고 요청 memberId 대신 JWT subject를 사용하며 쿠키를 지운다")
    void withdrawMember_ValidJwtWithConfirmation_PassesConfirmedIdsAndClearsCookie() throws Exception {
        // given
        String token = authenticatedToken(7L);

        // when
        var response = mockMvc.perform(delete("/api/v1/members/me")
                .param("memberId", "999")
                .param("confirmedDeletionProjectIds", "2,5,2")
                .header("Authorization", "Bearer " + token));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MEMBER200_4"));
        assertTrue(clearsRefreshToken(response.andReturn().getResponse().getHeaders("Set-Cookie")));
        verify(withdrawalCommandService).withdraw(7L, Set.of(2L, 5L));
    }

    @Test
    @DisplayName("확인 ID를 생략하면 빈 집합으로 탈퇴를 진행한다")
    void withdrawMember_WithoutConfirmation_PassesEmptySet() throws Exception {
        // given
        String token = authenticatedToken(7L);

        // when
        var response = mockMvc.perform(delete("/api/v1/members/me")
                .header("Authorization", "Bearer " + token));

        // then
        response.andExpect(status().isOk());
        verify(withdrawalCommandService).withdraw(7L, Set.of());
    }

    @Test
    @DisplayName("확인하지 않은 삭제 프로젝트가 있으면 409 MEMBER409_3을 반환하고 쿠키를 지우지 않는다")
    void withdrawMember_DeletionNotConfirmed_ReturnsConflictWithoutClearingCookie() throws Exception {
        // given
        String token = authenticatedToken(7L);
        doThrow(new MemberException(MemberErrorCode.WITHDRAWAL_DELETION_NOT_CONFIRMED))
                .when(withdrawalCommandService).withdraw(anyLong(), any());

        // when
        var response = mockMvc.perform(delete("/api/v1/members/me")
                .header("Authorization", "Bearer " + token));

        // then
        response.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER409_3"));
        assertFalse(clearsRefreshToken(response.andReturn().getResponse().getHeaders("Set-Cookie")));
    }

    @Test
    @DisplayName("확인 ID가 숫자가 아니면 서비스를 호출하지 않고 오류로 끝난다")
    void withdrawMember_NonNumericConfirmation_RejectsWithoutCallingService() throws Exception {
        // given
        String token = authenticatedToken(7L);

        // when
        var response = mockMvc.perform(delete("/api/v1/members/me")
                .param("confirmedDeletionProjectIds", "abc")
                .header("Authorization", "Bearer " + token));

        // then
        // 타입 불일치가 400이 아닌 500이 되는 것은 GeneralExceptionAdvice의 기존 전역 동작이라 상태 코드는 단정하지 않는다.
        assertTrue(response.andReturn().getResponse().getStatus() >= 400);
        verifyNoInteractions(withdrawalCommandService);
    }

    @Test
    @DisplayName("탈퇴 안내 조회는 검증된 JWT subject로 조회하고 승계·삭제 결과를 반환한다")
    void getWithdrawalPreview_ValidJwt_ReturnsOutcomes() throws Exception {
        // given
        String token = authenticatedToken(7L);
        when(withdrawalQueryService.getWithdrawalPreview(7L)).thenReturn(
                MemberResDTO.WithdrawalPreview.builder().ownedProjects(List.of(
                        MemberResDTO.WithdrawalOwnedProject.builder()
                                .projectId(1L).title("A").outcome(WithdrawalProjectOutcome.SUCCESSION).build(),
                        MemberResDTO.WithdrawalOwnedProject.builder()
                                .projectId(2L).title("B").outcome(WithdrawalProjectOutcome.DELETION).build()))
                        .build());

        // when
        var response = mockMvc.perform(get("/api/v1/members/me/withdrawal-preview")
                .param("memberId", "999")
                .header("Authorization", "Bearer " + token));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MEMBER200_6"))
                .andExpect(jsonPath("$.result.ownedProjects[0].outcome").value("SUCCESSION"))
                .andExpect(jsonPath("$.result.ownedProjects[1].projectId").value(2))
                .andExpect(jsonPath("$.result.ownedProjects[1].outcome").value("DELETION"));
        verify(withdrawalQueryService).getWithdrawalPreview(7L);
    }

    // Spring Security의 CSRF 쿠키(XSRF-TOKEN)도 Set-Cookie에 실리므로 refresh_token 삭제 쿠키만 골라 확인한다.
    private boolean clearsRefreshToken(List<String> setCookieHeaders) {
        return setCookieHeaders.stream()
                .anyMatch(cookie -> cookie.startsWith("refresh_token=") && cookie.contains("Max-Age=0"));
    }

    private String authenticatedToken(Long memberId) {
        Member member = Member.builder().email("test@example.com").nickname("tester")
                .password("test-only").isActive(true).role(Role.ROLE_USER).build();
        ReflectionTestUtils.setField(member, "id", memberId);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        return jwtUtil.createAccessToken(memberId, Role.ROLE_USER);
    }
}
