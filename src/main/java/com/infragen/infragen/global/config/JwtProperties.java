package com.infragen.infragen.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// 워크북에서 했던 것처럼 JwtUtil 클래스의 생성자 부분에 @Value 어노테이션으로 application.yml 파일의 내용을 직접적으로 읽어들이지 않고, 스프링 빈으로써 관리
@Getter
@Setter // yaml의 값을 필드에 주입하기 위해 Setter가 필요 (ConfigurationProperties의 작동 원리)
@Configuration
@ConfigurationProperties(prefix = "jwt") // application.yaml에서 jwt로 시작하는 설정값들을 이 클래스의 필드와 1:1로 매핑
public class JwtProperties {
    // 토큰의 서명을 생성 및 검증할 때 사용되는 키
    private String secret;
    // 토큰을 발급하는 주체를 명시하여, 우리 서버에서 만든 토큰이 맞는지 확인
    private String issuer;
    // access-token 섹션에 적힌 값을 저장하는 필드
    private AccessToken accessToken = new AccessToken();
    // refresh-token
    private RefreshToken refreshToken = new RefreshToken();
    // dev-token
    private DevToken devToken = new DevToken();

    // 인스턴스 없이도 내부 객체를 만들 수 있어야 하므로 static으로 선언
    @Getter
    @Setter
    public static class AccessToken {
        private long expirationTime;
    }

    @Getter
    @Setter
    public static class RefreshToken {
        private long expirationTime;
    }

    @Getter
    @Setter
    public static class DevToken {
        private long expirationTime;
    }
}
