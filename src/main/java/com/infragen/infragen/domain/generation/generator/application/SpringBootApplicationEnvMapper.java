package com.infragen.infragen.domain.generation.generator.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

/**
 * Spring Boot 애플리케이션의 DB 접속 변수 규칙을 제공한다.
 *
 * <p>관계형 DB는 하나일 때만 JDBC 접속 정보로 만들어 {@code SPRING_DATASOURCE_*}로 매핑한다. 관계형이 아닌 DB는
 * 종류별 규칙으로 매핑하며(MongoDB는 {@code SPRING_MONGODB_*}), 새 종류는 규칙표에 항목을 더해 지원한다.
 */
@Component
public class SpringBootApplicationEnvMapper implements ApplicationEnvMapper {

    private final Map<ComponentType, Function<DatabaseConnection, SequencedMap<String, String>>> nonRelationalRules =
        Map.of(ComponentType.MONGODB, this::mongoEnvironment);

    @Override
    public ComponentType getApplicationType() {
        return ComponentType.SPRING_BOOT;
    }

    @Override
    public SequencedMap<String, String> databaseEnvironment(
        SequencedMap<ComponentType, DatabaseConnection> connections
    ) {
        SequencedMap<String, String> environment = new LinkedHashMap<>();
        environment.putAll(datasourceEnvironment(connections));
        connections.forEach((type, connection) -> {
            if (!connection.relational()) {
                environment.putAll(nonRelationalRule(type).apply(connection));
            }
        });
        return environment;
    }

    @Override
    public Optional<String> multipleDatabaseNotice(
        SequencedMap<ComponentType, DatabaseConnection> connections,
        String envSource
    ) {
        if (relationalConnections(connections).size() < 2) {
            return Optional.empty();
        }
        return Optional.of("""
            # 애플리케이션에 데이터베이스가 2개 이상 연결되어 SPRING_DATASOURCE_*를 생성하지 않았습니다.
            # DataSource를 직접 설정하고 %s의 DB별 접속 변수를 사용하세요.
            """.formatted(envSource));
    }

    private SequencedMap<String, String> datasourceEnvironment(
        SequencedMap<ComponentType, DatabaseConnection> connections
    ) {
        SequencedMap<String, String> environment = new LinkedHashMap<>();
        List<DatabaseConnection> relationalConnections = relationalConnections(connections);
        if (relationalConnections.size() != 1) {
            return environment;
        }
        DatabaseConnection connection = relationalConnections.getFirst();
        environment.put("SPRING_DATASOURCE_URL", "jdbc:" + connection.scheme() + "://" + connection.host()
            + ":" + connection.port() + "/" + connection.database());
        environment.put("SPRING_DATASOURCE_USERNAME", connection.username());
        environment.put("SPRING_DATASOURCE_PASSWORD", connection.password());
        return environment;
    }

    private SequencedMap<String, String> mongoEnvironment(DatabaseConnection connection) {
        SequencedMap<String, String> environment = new LinkedHashMap<>();
        environment.put("SPRING_MONGODB_HOST", connection.host());
        environment.put("SPRING_MONGODB_PORT", connection.port());
        environment.put("SPRING_MONGODB_DATABASE", connection.database());
        environment.put("SPRING_MONGODB_USERNAME", connection.username());
        environment.put("SPRING_MONGODB_PASSWORD", connection.password());
        connection.authenticationDatabase().ifPresent(
            authenticationDatabase -> environment.put("SPRING_MONGODB_AUTHENTICATION_DATABASE", authenticationDatabase));
        return environment;
    }

    // 규칙 없는 종류를 무시하면 접속 변수가 조용히 빠지므로 내부 계약 위반으로 거부한다.
    private Function<DatabaseConnection, SequencedMap<String, String>> nonRelationalRule(ComponentType type) {
        Function<DatabaseConnection, SequencedMap<String, String>> rule = nonRelationalRules.get(type);
        if (rule == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }
        return rule;
    }

    private List<DatabaseConnection> relationalConnections(
        SequencedMap<ComponentType, DatabaseConnection> connections
    ) {
        return connections.values().stream()
            // MongoDB 같은 비관계형 DB는 DataSource가 아니므로 개수 판단에서 제외한다.
            .filter(DatabaseConnection::relational)
            .toList();
    }
}
