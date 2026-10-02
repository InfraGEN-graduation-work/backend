package com.infragen.infragen.domain.generation.generator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.global.enums.ComponentType;

class SpringBootApplicationEnvMapperTest {

    private final SpringBootApplicationEnvMapper mapper = new SpringBootApplicationEnvMapper();

    @Test
    @DisplayName("Spring Boot 앱 타입을 지원한다")
    void supportsSpringBoot() {
        assertEquals(ComponentType.SPRING_BOOT, mapper.getApplicationType());
    }

    @Test
    @DisplayName("JDBC 접속 정보를 SPRING_DATASOURCE 변수로 URL, USERNAME, PASSWORD 순서로 매핑한다")
    void mapsDatasourceEnvironmentInOrder() {
        var environment = mapper.datasourceEnvironment("jdbc:mysql://localhost:3306/db", "user", "pw");

        assertEquals(
            List.of("SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD"),
            List.copyOf(environment.keySet())
        );
        assertEquals(
            List.of("jdbc:mysql://localhost:3306/db", "user", "pw"),
            List.copyOf(environment.values())
        );
    }

    @Test
    @DisplayName("DB 여러 개 안내 주석에 접속 변수 위치 이름을 넣는다")
    void multipleDatabaseNoticeUsesEnvSource() {
        assertEquals("""
            # 애플리케이션에 데이터베이스가 2개 이상 연결되어 SPRING_DATASOURCE_*를 생성하지 않았습니다.
            # DataSource를 직접 설정하고 .env의 DB별 접속 변수를 사용하세요.
            """, mapper.multipleDatabaseNotice(".env"));
    }
}
