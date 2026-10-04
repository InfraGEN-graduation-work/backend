package com.infragen.infragen.domain.generation.generator.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
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

    @Override
    public SequencedMap<String, String> databaseEnvironment(List<DatabaseConnection> connections) {
        List<DatabaseConnection> jdbcConnections = jdbcConnections(connections);
        if (jdbcConnections.size() != 1) {
            return new LinkedHashMap<>();
        }
        DatabaseConnection connection = jdbcConnections.getFirst();
        String url = "jdbc:" + connection.scheme() + "://" + connection.host() + ":" + connection.port()
            + "/" + connection.database();
        return datasourceEnvironment(url, connection.username(), connection.password());
    }

    @Override
    public Optional<String> multipleDatabaseNotice(List<DatabaseConnection> connections, String envSource) {
        if (jdbcConnections(connections).size() < 2) {
            return Optional.empty();
        }
        return Optional.of(multipleDatabaseNotice(envSource));
    }

    private List<DatabaseConnection> jdbcConnections(List<DatabaseConnection> connections) {
        return connections.stream()
            // MongoDB 같은 비JDBC DB는 DataSource가 아니므로 개수 판단에서 제외한다.
            .filter(DatabaseConnection::jdbc)
            .toList();
    }
}
