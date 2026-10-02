package com.infragen.infragen.domain.generation.generator.compose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.generation.generator.compose.HostAppEnvContributor.JdbcConnection;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLEnvComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLEnvComponent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LOCAL_DEV 호스트 실행 앱 접속 정보 contributor")
class HostAppEnvContributorTest {

    @Test
    @DisplayName("MySQL — localhost와 사용자 입력 호스트 포트로 MYSQL_HOST/PORT를 순서대로 만든다")
    void hostAppEnvironment_Mysql_ReturnsNeutralVariablesInOrder() {
        // given
        HostAppEnvContributor contributor = new MysqlHostAppEnvContributor();

        // when
        SequencedMap<String, String> environment = contributor.hostAppEnvironment(mysqlComponent(mysqlEnv()));

        // then
        assertEquals(
            List.of(Map.entry("MYSQL_HOST", "localhost"), Map.entry("MYSQL_PORT", "3307")),
            List.copyOf(environment.entrySet())
        );
    }

    @Test
    @DisplayName("MySQL — 호스트 포트와 env 값으로 JDBC 연결 정보를 만든다")
    void jdbcConnection_Mysql_ReturnsLocalhostJdbcConnection() {
        // given
        HostAppEnvContributor contributor = new MysqlHostAppEnvContributor();

        // when
        Optional<JdbcConnection> connection = contributor.jdbcConnection(mysqlComponent(mysqlEnv()));

        // then
        assertEquals(
            Optional.of(new JdbcConnection("jdbc:mysql://localhost:3307/appdb", "user", "userpass12")),
            connection
        );
    }

    @Test
    @DisplayName("MySQL env 누락 — JDBC 연결 정보 생성 시 INVALID_COMPONENT_STATE")
    void jdbcConnection_MysqlEnvMissing_ThrowsGenerationException() {
        // given
        HostAppEnvContributor contributor = new MysqlHostAppEnvContributor();
        MySQLComponent mysql = mysqlComponent(null);

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> contributor.jdbcConnection(mysql)
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
    }

    @Test
    @DisplayName("PostgreSQL — localhost와 사용자 입력 호스트 포트로 POSTGRES_HOST/PORT를 순서대로 만든다")
    void hostAppEnvironment_Postgres_ReturnsNeutralVariablesInOrder() {
        // given
        HostAppEnvContributor contributor = new PostgresHostAppEnvContributor();

        // when
        SequencedMap<String, String> environment = contributor.hostAppEnvironment(postgresComponent(postgresEnv()));

        // then
        assertEquals(
            List.of(Map.entry("POSTGRES_HOST", "localhost"), Map.entry("POSTGRES_PORT", "5433")),
            List.copyOf(environment.entrySet())
        );
    }

    @Test
    @DisplayName("PostgreSQL — 호스트 포트와 env 값으로 JDBC 연결 정보를 만든다")
    void jdbcConnection_Postgres_ReturnsLocalhostJdbcConnection() {
        // given
        HostAppEnvContributor contributor = new PostgresHostAppEnvContributor();

        // when
        Optional<JdbcConnection> connection = contributor.jdbcConnection(postgresComponent(postgresEnv()));

        // then
        assertEquals(
            Optional.of(new JdbcConnection("jdbc:postgresql://localhost:5433/pgdb", "pguser", "pgpass1234")),
            connection
        );
    }

    @Test
    @DisplayName("PostgreSQL env 누락 — JDBC 연결 정보 생성 시 INVALID_COMPONENT_STATE")
    void jdbcConnection_PostgresEnvMissing_ThrowsGenerationException() {
        // given
        HostAppEnvContributor contributor = new PostgresHostAppEnvContributor();
        PostgreSQLComponent postgres = postgresComponent(null);

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> contributor.jdbcConnection(postgres)
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
    }

    private static MySQLEnvComponent mysqlEnv() {
        return MySQLEnvComponent.builder()
            .databaseName("appdb")
            .username("user")
            .userPassword("userpass12")
            .rootPassword("rootpass12")
            .build();
    }

    private static MySQLComponent mysqlComponent(MySQLEnvComponent env) {
        return MySQLComponent.builder()
            .id("mysql-1")
            .posX(0f)
            .posY(0f)
            .imageVersion("mysql:8.0")
            .containerName("mysql")
            .port(3307)
            .env(env)
            .build();
    }

    private static PostgreSQLEnvComponent postgresEnv() {
        return PostgreSQLEnvComponent.builder()
            .databaseName("pgdb")
            .username("pguser")
            .password("pgpass1234")
            .build();
    }

    private static PostgreSQLComponent postgresComponent(PostgreSQLEnvComponent env) {
        return PostgreSQLComponent.builder()
            .id("pg-1")
            .posX(0f)
            .posY(0f)
            .imageVersion("postgres:17")
            .containerName("postgres")
            .port(5433)
            .env(env)
            .build();
    }
}
