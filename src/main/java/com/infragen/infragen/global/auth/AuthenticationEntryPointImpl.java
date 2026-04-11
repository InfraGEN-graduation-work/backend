package com.infragen.infragen.global.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException authException
    ) throws IOException, ServletException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); // http 상태 코드를 401로 설정
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 응답 타입 지정(json)
        response.setCharacterEncoding("UTF-8"); // 인코딩 설정
        ApiResponse<Void> errorResponse = ApiResponse.onFailure(
                AuthErrorCode.UNAUTHORIZED,
                null
        );

        // 응답 본문에 json 작성
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
