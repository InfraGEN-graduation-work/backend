package com.infragen.infragen.domain.generation.generator.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;

import org.springframework.stereotype.Component;

import com.infragen.infragen.global.enums.ComponentType;

/**
 * Spring Boot 애플리케이션의 DB 접속 변수 규칙을 제공한다.
 *
 * <p>관계형 DB는 하나일 때만 JDBC 접속 정보로 만들어 {@code SPRING_DATASOURCE_*}로, MongoDB는 {@code SPRING_MONGODB_*}로 매핑한다.
 */
@Component
public class SpringBootApplicationEnvMapper implements ApplicationEnvMapper {

    private static final String MONGODB_SCHEME = "mongodb";

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

    @Override
    public SequencedMap<String, String> databaseEnvironment(List<DatabaseConnection> connections) {
        SequencedMap<String, String> environment = new LinkedHashMap<>();
        environment.putAll(jdbcEnvironment(connections));
        environment.putAll(mongoEnvironment(connections));
        return environment;
    }

    @Override
    public Optional<String> multipleDatabaseNotice(List<DatabaseConnection> connections, String envSource) {
        if (relationalConnections(connections).size() < 2) {
            return Optional.empty();
        }
        return Optional.of(multipleDatabaseNotice(envSource));
    }

    private SequencedMap<String, String> jdbcEnvironment(List<DatabaseConnection> connections) {
        List<DatabaseConnection> relationalConnections = relationalConnections(connections);
        if (relationalConnections.size() != 1) {
            return new LinkedHashMap<>();
        }
        DatabaseConnection connection = relationalConnections.getFirst();
        String url = "jdbc:" + connection.scheme() + "://" + connection.host() + ":" + connection.port()
            + "/" + connection.database();
        return datasourceEnvironment(url, connection.username(), connection.password());
    }

    // 같은 타입 DB 중복은 parsing에서 거부되므로 MongoDB가 정확히 하나일 때만 매핑한다.
    private SequencedMap<String, String> mongoEnvironment(List<DatabaseConnection> connections) {
        List<DatabaseConnection> mongoConnections = connections.stream()
            .filter(connection -> MONGODB_SCHEME.equals(connection.scheme()))
            .toList();
        SequencedMap<String, String> environment = new LinkedHashMap<>();
        if (mongoConnections.size() != 1) {
            return environment;
        }
        DatabaseConnection connection = mongoConnections.getFirst();
        environment.put("SPRING_MONGODB_HOST", connection.host());
        environment.put("SPRING_MONGODB_PORT", connection.port());
        environment.put("SPRING_MONGODB_DATABASE", connection.database());
        environment.put("SPRING_MONGODB_USERNAME", connection.username());
        environment.put("SPRING_MONGODB_PASSWORD", connection.password());
        connection.authenticationDatabase().ifPresent(
            authenticationDatabase -> environment.put("SPRING_MONGODB_AUTHENTICATION_DATABASE", authenticationDatabase));
        return environment;
    }

    private List<DatabaseConnection> relationalConnections(List<DatabaseConnection> connections) {
        return connections.stream()
            // MongoDB 같은 비관계형 DB는 DataSource가 아니므로 개수 판단에서 제외한다.
            .filter(DatabaseConnection::relational)
            .toList();
    }
}
