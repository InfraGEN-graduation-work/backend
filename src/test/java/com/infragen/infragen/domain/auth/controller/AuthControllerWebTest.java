package com.infragen.infragen.domain.auth.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.SET_COOKIE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.infragen.infragen.domain.auth.dto.response.AuthResDTO;
import com.infragen.infragen.domain.auth.service.AuthService;
import com.infragen.infragen.global.apiPayload.handler.GeneralExceptionAdvice;
import com.infragen.infragen.global.auth.RefreshTokenCookieWriter;
import com.infragen.infragen.global.properties.JwtProperties;

@WebMvcTest(AuthController.class)
@ContextConfiguration(classes = {
        AuthControllerWebTest.ControllerWebTestApplication.class,
        AuthController.class,
        GeneralExceptionAdvice.class,
        RefreshTokenCookieWriter.class
})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController Web 테스트")
class AuthControllerWebTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(AuthControllerWebTest.WebMvcTestConfig.class)
    static class ControllerWebTestApplication {
    }

    @TestConfiguration
    static class WebMvcTestConfig {
    }

    @Test
    @DisplayName("guest endpoint는 access token과 refresh cookie를 반환한다")
    void guestLogin_ReturnsTokenAndRefreshCookie() throws Exception {
        // given
        JwtProperties.RefreshToken refreshTokenProperties = new JwtProperties.RefreshToken();
        refreshTokenProperties.setExpirationTime(3_600_000L);

        when(jwtProperties.getRefreshToken()).thenReturn(refreshTokenProperties);
        when(authService.guestLogin()).thenReturn(
                new AuthResDTO.TokenResultDTO(
                        "guest-access-token",
                        "guest-refresh-token"
                )
        );

        // when
        var response = mockMvc.perform(post("/api/v1/auth/guest"));

        // then
        response
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("AUTH200_2"))
                .andExpect(jsonPath("$.result.accessToken").value("guest-access-token"))
                .andExpect(header().string(
                        SET_COOKIE,
                        allOf(
                                containsString("refresh_token=guest-refresh-token"),
                                containsString("Max-Age=3600"),
                                containsString("HttpOnly")
                        )
                ));

        verify(authService).guestLogin();
    }
}
