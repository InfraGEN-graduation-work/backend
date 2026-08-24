package com.infragen.infragen.global.auth.filter;

import tools.jackson.databind.ObjectMapper;
import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import com.infragen.infragen.global.apiPayload.code.GeneralErrorCode;
import com.infragen.infragen.global.apiPayload.exception.GeneralException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtExceptionFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            log.warn("jwt 토큰이 만료되었습니다: {}", e.getMessage());
            setErrorResponse(response, AuthErrorCode.TOKEN_EXPIRED);
        } catch (MalformedJwtException e) {
            log.warn("jwt 토큰이 유효하지 않습니다.  : {}", e.getMessage());
            setErrorResponse(response, AuthErrorCode.TOKEN_INVALID);
        } catch (AuthException e) {
            log.warn("인증 예외 발생: {}", e.getMessage());
            setErrorResponse(response, e.getCode());
        } catch (GeneralException e) {
            log.warn("jwt 관련 예외 발생: {}", e.getMessage());
            setErrorResponse(response, e.getCode());
        } catch (Exception e) {
            log.error("예상치 못한 jwt filter chain 관련 예외입니다.: {}", e.getMessage());
            setErrorResponse(response, GeneralErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void setErrorResponse(HttpServletResponse response, BaseErrorCode code) throws IOException {
        response.setStatus(code.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); // application/json을 의미
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> errorResponse = ApiResponse.onFailure(
                code,
                null
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
