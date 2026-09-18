package com.infragen.infragen.domain.auth.controller;

import com.infragen.infragen.domain.auth.dto.request.AuthReqDTO;
import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import com.infragen.infragen.domain.auth.service.AuthService;
import com.infragen.infragen.domain.auth.service.EmailVerificationService;
import com.infragen.infragen.domain.member.repository.MemberRepository;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class, properties = {
        "cors.allowed-origins=http://localhost",
        "jwt.secret=email-test-secret-email-test-secret-email-test-secret"
})
@ContextConfiguration(classes = AuthControllerEmailWebTest.Config.class)
class AuthControllerEmailWebTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(JwtProperties.class)
    @Import({AuthController.class, GeneralExceptionAdvice.class, SecurityConfig.class,
            JwtUtil.class, JwtAuthFilter.class, JwtExceptionFilter.class,
            AuthenticationEntryPointImpl.class, CustomUserDetailsService.class})
    static class Config {
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;
    @MockitoBean EmailVerificationService emailVerificationService;
    @MockitoBean RefreshTokenCookieWriter cookieWriter;
    @MockitoBean RedisUtil redisUtil;
    @MockitoBean MemberRepository memberRepository;

    @Test
    void sendEmailCode_Anonymous_Allowed() throws Exception {
        // given
        var request = post("/api/v1/auth/email/code").contentType(APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\"}");

        // when
        var response = mockMvc.perform(request);

        // then
        response.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("AUTH200_4"));
        verify(emailVerificationService).sendCode("user@example.com");
    }

    @Test
    void sendEmailCode_InvalidEmail_RejectsBeforeSending() throws Exception {
        // given
        var request = post("/api/v1/auth/email/code").contentType(APPLICATION_JSON)
                .content("{\"email\":\"invalid\"}");

        // when
        var response = mockMvc.perform(request);

        // then
        response.andExpect(status().isBadRequest());
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void sendEmailCode_Throttled_Returns429() throws Exception {
        // given
        doThrow(new AuthException(AuthErrorCode.EMAIL_CODE_RATE_LIMITED))
                .when(emailVerificationService).sendCode("user@example.com");
        var request = post("/api/v1/auth/email/code").contentType(APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\"}");

        // when
        var response = mockMvc.perform(request);

        // then
        response.andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("AUTH429_1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"12345\"", "\"abcdef\""})
    void signup_InvalidCode_RejectsBeforeService(String codeJson) throws Exception {
        // given
        var request = post("/api/v1/auth/signup").contentType(APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"password123\","
                        + "\"nickname\":\"user\",\"verificationCode\":" + codeJson + "}");

        // when
        var response = mockMvc.perform(request);

        // then
        response.andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    void signup_ValidRequest_PreservesLeadingZero() throws Exception {
        // given
        var request = post("/api/v1/auth/signup").contentType(APPLICATION_JSON)
                .content("""
                        {"email":"user@example.com","password":"password123",
                         "nickname":"user","verificationCode":"012345"}
                        """);
        var captured = ArgumentCaptor.forClass(AuthReqDTO.SignupDTO.class);

        // when
        var response = mockMvc.perform(request);

        // then
        response.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("AUTH201_1"));
        verify(authService).signup(captured.capture());
        assertEquals("012345", captured.getValue().getVerificationCode());
    }
}
