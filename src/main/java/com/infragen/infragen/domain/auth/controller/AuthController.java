package com.infragen.infragen.domain.auth.controller;

import com.infragen.infragen.domain.auth.dto.request.AuthReqDTO;
import com.infragen.infragen.domain.auth.dto.response.AuthResDTO;
import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import com.infragen.infragen.domain.auth.exception.code.success.AuthSuccessCode;
import com.infragen.infragen.domain.auth.service.AuthService;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.properties.JwtProperties;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final JwtProperties jwtProperties;

    // 일반 회원가입
    @PostMapping("/signup") 
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResDTO.AccessTokenResultDTO> signup(@RequestBody @Valid AuthReqDTO.SignupDTO request, HttpServletResponse response) {
        AuthResDTO.TokenResultDTO tokens = authService.signup(request);
        return handleTokenResponse(tokens, response, AuthSuccessCode.SIGNUP_SUCCESS);
    }

    // 일반 로그인
    @PostMapping("/login")
    public ApiResponse<AuthResDTO.AccessTokenResultDTO> login(@RequestBody @Valid AuthReqDTO.LoginDTO request, HttpServletResponse response) {
        AuthResDTO.TokenResultDTO tokens = authService.login(request);
        return handleTokenResponse(tokens, response, AuthSuccessCode.LOGIN_SUCCESS);
    }

    // 소셜 로그인
    @PostMapping("/login/{provider}")
    public ApiResponse<AuthResDTO.AccessTokenResultDTO> socialLogin(@PathVariable("provider") String provider, @RequestBody @Valid AuthReqDTO.SocialLoginDTO request, HttpServletResponse response) {
        AuthResDTO.TokenResultDTO tokens = authService.socialLogin(provider, request);
        return handleTokenResponse(tokens, response, AuthSuccessCode.LOGIN_SUCCESS);
    }

    // 로그아웃
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String accessToken, HttpServletResponse response) {
        authService.logout(accessToken);
        setRefreshTokenCookie(response, "", 0);
        return ApiResponse.onSuccess(AuthSuccessCode.LOGOUT_SUCCESS, null);
    }

    // 토큰 재발급
    @PostMapping("/reissue")
    public ApiResponse<AuthResDTO.AccessTokenResultDTO> reissueToken(
        @CookieValue(value = "refresh_token", required = false) String refreshToken,
        HttpServletResponse response
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }
        AuthResDTO.TokenResultDTO tokens = authService.reissueToken(refreshToken);
        return handleTokenResponse(tokens, response, AuthSuccessCode.TOKEN_REFRESH_SUCCESS);
    }

    // 토큰 응답 공통 처리 헬퍼
    private ApiResponse<AuthResDTO.AccessTokenResultDTO> handleTokenResponse(AuthResDTO.TokenResultDTO tokens, HttpServletResponse response, AuthSuccessCode successCode) {
        int maxAge = (int) (jwtProperties.getRefreshToken().getExpirationTime() / 1000);
        setRefreshTokenCookie(response, tokens.getRefreshToken(), maxAge);
        return ApiResponse.onSuccess(successCode, new AuthResDTO.AccessTokenResultDTO(tokens.getAccessToken()));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, int maxAge) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
