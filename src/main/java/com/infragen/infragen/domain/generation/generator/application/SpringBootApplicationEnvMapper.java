package com.infragen.infragen.domain.generation.generator.application;

import java.util.LinkedHashMap;
import java.util.SequencedMap;

import org.springframework.stereotype.Component;

import com.infragen.infragen.global.enums.ComponentType;

/** Spring Boot 애플리케이션의 {@code SPRING_DATASOURCE_*} 변수 규칙을 제공한다. */
@Component
public class SpringBootApplicationEnvMapper implements ApplicationEnvMapper {

    @Override
    public ComponentType getApplicationType() {
        return ComponentType.SPRING_BOOT;
    }

    @Override
    public SequencedMap<String, String> datasourceEnvironment(String url, String username, String password) {
        SequencedMap<String, String> environment = new LinkedHashMap<>();
        environment.put("SPRING_DATASOURCE_URL", url);
        environment.put("SPRING_DATASOURCE_USERNAME", username);
        environment.put("SPRING_DATASOURCE_PASSWORD", password);
        return environment;
    }

    @Override
    public String multipleDatabaseNotice(String envSource) {
        return """
            # 애플리케이션에 데이터베이스가 2개 이상 연결되어 SPRING_DATASOURCE_*를 생성하지 않았습니다.
            # DataSource를 직접 설정하고 %s의 DB별 접속 변수를 사용하세요.
            """.formatted(envSource);
    }
}
