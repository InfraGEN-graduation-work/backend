package com.infragen.infragen.global.config;

import com.infragen.infragen.global.auth.AuthenticationEntryPointImpl;
import com.infragen.infragen.global.auth.filter.JwtAuthFilter;
import com.infragen.infragen.global.auth.filter.JwtExceptionFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final JwtExceptionFilter jwtExceptionFilter;
    private final AuthenticationEntryPointImpl authenticationEntryPointImpl;

    private final String[] allowUris = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/api/v1/auth/**",
            "/health" // 인증 관련해서는 jwt 토큰 인증 없이도 요청을 보낼 수 있어야 함

    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // csrf 보호 비활성화(어느정도 개발 이후에 csrf 토큰 적용 예정)
                .formLogin(AbstractHttpConfigurer::disable) // 폼 로그인 비활성화
                .httpBasic(AbstractHttpConfigurer::disable) // http basic 인증 방식 비활성화
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS)) // jwt 기반 인증을 사용하므로, 세션을 생성하지 않게끔(stateless 방식으로 설정)
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(authenticationEntryPointImpl)) // 인증 실패 시 처리(예외 처리 설정)
                .authorizeHttpRequests(requests ->
                        requests.requestMatchers(allowUris).permitAll() // 허용된 uri는 접근 가능
                                .anyRequest().authenticated()) // 그 외 요청은 반드시 인증 필요 명시
                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class) // UsernamePasswordAuthenticationFilter 이전에 JwtAuthFilter를 먼저 실행
                .addFilterBefore(jwtExceptionFilter,
                        JwtAuthFilter.class); // JwtAuthFilter 이전에 JwtExceptionFilter를 먼저 실행(jwt 관련 예외 처리)

        return http.build();
    }
}
