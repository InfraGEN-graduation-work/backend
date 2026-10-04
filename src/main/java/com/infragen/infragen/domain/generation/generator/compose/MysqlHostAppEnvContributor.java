package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.generation.generator.application.DatabaseConnection;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLEnvComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;
import org.springframework.stereotype.Component;

/**
 * 호스트에서 실행하는 애플리케이션의 MySQL 접속 정보를 만든다.
 *
 * <p>앱은 Compose 밖(호스트)에서 실행되므로 컨테이너 DNS가 아니라 {@code localhost}와 사용자 입력 호스트 포트로 접속한다.
 */
@Component
public class MysqlHostAppEnvContributor implements HostAppEnvContributor {
    private static final String LOCALHOST = "localhost";

    @Override
    public ComponentType getDependencyType() {
        return ComponentType.MYSQL;
    }

    @Override
    public SequencedMap<String, String> hostAppEnvironment(BaseComponent dependency) {
        MySQLComponent mysql = (MySQLComponent) dependency;
        SequencedMap<String, String> environment = new LinkedHashMap<>();
        environment.put("MYSQL_HOST", LOCALHOST);
        environment.put("MYSQL_PORT", String.valueOf(mysql.getPort()));
        return environment;
    }

    /**
     * @throws IaCGenerationException MySQL env 정보가 없는 경우
     */
    @Override
    public Optional<DatabaseConnection> databaseConnection(BaseComponent dependency) {
        MySQLComponent mysql = (MySQLComponent) dependency;
        MySQLEnvComponent env = mysql.getEnv();
        if (env == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }

        return Optional.of(new DatabaseConnection(
            "mysql",
            true,
            LOCALHOST,
            String.valueOf(mysql.getPort()),
            env.getDatabaseName(),
            env.getUsername(),
            env.getUserPassword()
        ));
    }
}
