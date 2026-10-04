package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.generation.generator.application.DatabaseConnection;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLEnvComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;
import org.springframework.stereotype.Component;

/**
 * 호스트에서 실행하는 애플리케이션의 PostgreSQL 접속 정보를 만든다.
 *
 * <p>앱은 Compose 밖(호스트)에서 실행되므로 컨테이너 DNS가 아니라 {@code localhost}와 사용자 입력 호스트 포트로 접속한다.
 */
@Component
public class PostgresHostAppEnvContributor implements HostAppEnvContributor {

    private static final String LOCALHOST = "localhost";

    @Override
    public ComponentType getDependencyType() {
        return ComponentType.POSTGRESQL;
    }

    @Override
    public SequencedMap<String, String> hostAppEnvironment(BaseComponent dependency) {
        PostgreSQLComponent postgres = (PostgreSQLComponent) dependency;
        SequencedMap<String, String> environment = new LinkedHashMap<>();
        environment.put("POSTGRES_HOST", LOCALHOST);
        environment.put("POSTGRES_PORT", String.valueOf(postgres.getPort()));
        return environment;
    }

    /**
     * @throws IaCGenerationException PostgreSQL env 정보가 없는 경우
     */
    @Override
    public Optional<DatabaseConnection> databaseConnection(BaseComponent dependency) {
        PostgreSQLComponent postgres = (PostgreSQLComponent) dependency;
        PostgreSQLEnvComponent env = postgres.getEnv();
        if (env == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }

        return Optional.of(new DatabaseConnection(
            "postgresql",
            true,
            LOCALHOST,
            String.valueOf(postgres.getPort()),
            env.getDatabaseName(),
            env.getUsername(),
            env.getPassword()
        ));
    }
}
