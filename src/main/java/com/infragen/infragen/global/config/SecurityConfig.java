package com.infragen.infragen.global.config;

import java.io.IOException;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.apiPayload.code.GeneralErrorCode;
import com.infragen.infragen.global.auth.AuthenticationEntryPointImpl;
import com.infragen.infragen.global.auth.filter.JwtAuthFilter;
import com.infragen.infragen.global.auth.filter.JwtExceptionFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final JwtExceptionFilter jwtExceptionFilter;
    private final AuthenticationEntryPointImpl authenticationEntryPointImpl;
    private final ObjectMapper objectMapper;

    private final String[] allowUris = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/api/v1/auth/signup",
            "/api/v1/auth/email/code",
            "/api/v1/auth/login",
            "/api/v1/auth/login/**",
            "/api/v1/auth/guest",
            "/api/v1/auth/reissue",
            "/api/v1/auth/csrf",
            "/ws/collaboration",
            "/health",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = csrfTokenRepository();
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()) // CSRF 토큰을 요청 속성에 저장하도록 설정
                        // CSRF 보호를 적용할 요청을 지정
                        .requireCsrfProtectionMatcher(request ->
                                HttpMethod.POST.matches(request.getMethod())
                                        && "/api/v1/auth/reissue".equals(request.getRequestURI()))
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS)) // jwt 기반 인증을 사용하므로, 세션을 생성하지 않게끔(stateless 방식으로 설정)
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(authenticationEntryPointImpl)
                                .accessDeniedHandler(csrfAccessDeniedHandler())) // 인증 실패 시 처리(예외 처리 설정)
                .authorizeHttpRequests(requests ->
                        requests.requestMatchers(allowUris).permitAll() // 허용된 uri는 접근 가능
                                .anyRequest().authenticated()) // 그 외 요청은 반드시 인증 필요 명시
                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class) // UsernamePasswordAuthenticationFilter 이전에 JwtAuthFilter를 먼저 실행
                .addFilterBefore(jwtExceptionFilter,
                        JwtAuthFilter.class) // JwtAuthFilter 이전에 JwtExceptionFilter를 먼저 실행(jwt 관련 예외 처리)
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class); // CsrfFilter 이후에 CsrfCookieFilter를 실행하여 CSRF 토큰을 쿠키에 저장하도록 설정

        return http.build();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookieName("XSRF-TOKEN");
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie
                .sameSite("None")
                .secure(true));
        return repository;
    }

    private AccessDeniedHandler csrfAccessDeniedHandler() {
        return (request, response, exception) -> {
            response.setStatus(GeneralErrorCode.FORBIDDEN.getHttpStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.onFailure(GeneralErrorCode.FORBIDDEN, null)
            ));
        };
    }

    private static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                response.setHeader(token.getHeaderName(), token.getToken());
            }
            filterChain.doFilter(request, response);
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins}") String allowedOrigins
    ) {
        // CORS 설정 객체 생성
        CorsConfiguration configuration = new CorsConfiguration();
        Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .forEach(configuration::addAllowedOriginPattern);
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        configuration.addExposedHeader("X-XSRF-TOKEN");
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        // CORS 설정을 적용할 URL 패턴 등록
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
