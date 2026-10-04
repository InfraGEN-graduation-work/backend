package com.infragen.infragen.domain.generation.generator.compose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.generation.generator.application.DatabaseConnection;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLEnvComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLEnvComponent;
import com.infragen.infragen.domain.parsing.dto.response.RedisComponent;
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
    @DisplayName("MySQL — 호스트 포트와 env 값으로 JDBC 구성요소 접속 정보를 만든다")
    void databaseConnection_Mysql_ReturnsLocalhostConnection() {
        // given
        HostAppEnvContributor contributor = new MysqlHostAppEnvContributor();

        // when
        Optional<DatabaseConnection> connection = contributor.databaseConnection(mysqlComponent(mysqlEnv()));

        // then
        assertEquals(
            Optional.of(new DatabaseConnection("mysql", true, "localhost", "3307", "appdb", "user", "userpass12")),
            connection
        );
    }

    @Test
    @DisplayName("MySQL env 누락 — 접속 정보 생성 시 INVALID_COMPONENT_STATE")
    void databaseConnection_MysqlEnvMissing_ThrowsGenerationException() {
        // given
        HostAppEnvContributor contributor = new MysqlHostAppEnvContributor();
        MySQLComponent component = mysqlComponent(null);

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> contributor.databaseConnection(component)
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
    @DisplayName("PostgreSQL — 호스트 포트와 env 값으로 JDBC 구성요소 접속 정보를 만든다")
    void databaseConnection_Postgres_ReturnsLocalhostConnection() {
        // given
        HostAppEnvContributor contributor = new PostgresHostAppEnvContributor();

        // when
        Optional<DatabaseConnection> connection = contributor.databaseConnection(postgresComponent(postgresEnv()));

        // then
        assertEquals(
            Optional.of(new DatabaseConnection("postgresql", true, "localhost", "5433", "pgdb", "pguser", "pgpass1234")),
            connection
        );
    }

    @Test
    @DisplayName("PostgreSQL env 누락 — 접속 정보 생성 시 INVALID_COMPONENT_STATE")
    void databaseConnection_PostgresEnvMissing_ThrowsGenerationException() {
        // given
        HostAppEnvContributor contributor = new PostgresHostAppEnvContributor();
        PostgreSQLComponent component = postgresComponent(null);

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> contributor.databaseConnection(component)
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
    }

    @Test
    @DisplayName("Redis — localhost, 사용자 입력 호스트 포트, password로 REDIS_HOST/PORT/PASSWORD를 순서대로 만든다")
    void hostAppEnvironment_Redis_ReturnsNeutralVariablesInOrder() {
        // given
        HostAppEnvContributor contributor = new RedisHostAppEnvContributor();

        // when
        SequencedMap<String, String> environment = contributor.hostAppEnvironment(redisComponent("redis-password"));

        // then
        assertEquals(
            List.of(
                Map.entry("REDIS_HOST", "localhost"),
                Map.entry("REDIS_PORT", "6380"),
                Map.entry("REDIS_PASSWORD", "redis-password")
            ),
            List.copyOf(environment.entrySet())
        );
    }

    @Test
    @DisplayName("Redis — DB 접속 정보가 없다")
    void databaseConnection_Redis_ReturnsEmpty() {
        // given
        HostAppEnvContributor contributor = new RedisHostAppEnvContributor();

        // when
        Optional<DatabaseConnection> connection = contributor.databaseConnection(redisComponent("redis-password"));

        // then
        assertTrue(connection.isEmpty());
    }

    @Test
    @DisplayName("Redis password 누락 — 중립 변수 생성 시 INVALID_COMPONENT_STATE")
    void hostAppEnvironment_RedisPasswordMissing_ThrowsGenerationException() {
        // given
        HostAppEnvContributor contributor = new RedisHostAppEnvContributor();
        RedisComponent redis = redisComponent(" ");

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> contributor.hostAppEnvironment(redis)
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
    }

    private static RedisComponent redisComponent(String password) {
        return RedisComponent.builder()
            .id("redis-1")
            .posX(0f)
            .posY(0f)
            .imageVersion("redis:7.4")
            .containerName("redis")
            .port(6380)
            .password(password)
            .build();
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
