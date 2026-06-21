package com.infragen.infragen.global.config;

import com.infragen.infragen.global.auth.AuthenticationEntryPointImpl;
import com.infragen.infragen.global.auth.filter.JwtAuthFilter;
import com.infragen.infragen.global.auth.filter.JwtExceptionFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

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
            "/api/v1/auth/**", // 인증 관련해서는 jwt 토큰 인증 없이도 요청을 보낼 수 있어야 함
            "/health",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${ALLOWED_ORIGINS:http://localhost:5173}") String allowedOrigins
    ) {
        // CORS 설정 객체 생성
        CorsConfiguration configuration = new CorsConfiguration();
        Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .forEach(configuration::addAllowedOrigin);
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
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
