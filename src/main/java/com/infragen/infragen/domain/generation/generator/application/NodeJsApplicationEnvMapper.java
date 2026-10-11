package com.infragen.infragen.domain.generation.generator.application;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;

import org.springframework.stereotype.Component;

import com.infragen.infragen.global.enums.ComponentType;

/**
 * Node.js 애플리케이션의 DB 접속 변수 규칙을 제공한다.
 *
 * <p>Node.js에는 Spring의 {@code SPRING_DATASOURCE_*}처럼 정해진 프레임워크 변수가 없어서 DB 부품이 만든 중립 변수
 * ({@code MYSQL_*}, {@code MONGO_*} 등)만 쓴다. 그래서 프레임워크 변수는 만들지 않고, DataSource 개수 충돌도 없어 안내 주석도 만들지 않는다.
 *
 * <p>매퍼가 등록되어 있어야 LOCAL_DEV generator가 "매퍼 없음" 경고를 남기지 않는다. 빈 결과가 정상 동작이다.
 */
@Component
public class NodeJsApplicationEnvMapper implements ApplicationEnvMapper {

    @Override
    public ComponentType getApplicationType() {
        return ComponentType.NODE_JS;
    }

    /** Node.js 앱은 관례상 {@code PORT}로 listen 포트를 읽으므로 그래프 포트를 넘긴다. */
    @Override
    public SequencedMap<String, String> cloudRuntimeEnvironment(int port) {
        SequencedMap<String, String> environment = new LinkedHashMap<>();
        environment.put("PORT", String.valueOf(port));
        return environment;
    }

    @Override
    public SequencedMap<String, String> databaseEnvironment(
        SequencedMap<ComponentType, DatabaseConnection> connections
    ) {
        return new LinkedHashMap<>();
    }

    @Override
    public Optional<String> multipleDatabaseNotice(
        SequencedMap<ComponentType, DatabaseConnection> connections,
        String envSource
    ) {
        return Optional.empty();
    }
}
