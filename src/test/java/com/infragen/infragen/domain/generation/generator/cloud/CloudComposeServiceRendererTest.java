package com.infragen.infragen.domain.generation.generator.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.infragen.infragen.domain.generation.generator.cloud.CloudComposeServiceRenderer.JdbcConnection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CLOUD_DEPLOY 의존 인프라 renderer의 앱 접속 정보")
class CloudComposeServiceRendererTest {

    @Test
    @DisplayName("MySQL — 서비스 DNS 기준 MYSQL_HOST/PORT를 순서대로 만든다")
    void applicationEnvironment_Mysql_ReturnsNeutralVariablesInOrder() {
        // given
        CloudComposeServiceRenderer renderer = new MysqlCloudComposeServiceRenderer();

        // when
        SequencedMap<String, String> environment = renderer.applicationEnvironment();

        // then
        assertEquals(
            List.of(Map.entry("MYSQL_HOST", "mysql"), Map.entry("MYSQL_PORT", "\"3306\"")),
            List.copyOf(environment.entrySet())
        );
    }

    @Test
    @DisplayName("MySQL — 외부 .env를 참조하는 JDBC 연결 정보를 만든다")
    void jdbcConnection_Mysql_ReturnsServiceDnsJdbcConnection() {
        // given
        CloudComposeServiceRenderer renderer = new MysqlCloudComposeServiceRenderer();

        // when
        Optional<JdbcConnection> connection = renderer.jdbcConnection();

        // then
        assertEquals(
            Optional.of(new JdbcConnection(
                "\"jdbc:mysql://mysql:3306/${MYSQL_DATABASE:?외부 .env에 설정 필요}\"",
                "\"${MYSQL_USER:?외부 .env에 설정 필요}\"",
                "\"${MYSQL_PASSWORD:?외부 .env에 설정 필요}\""
            )),
            connection
        );
    }

    @Test
    @DisplayName("PostgreSQL — 서비스 DNS 기준 POSTGRES_HOST/PORT를 순서대로 만든다")
    void applicationEnvironment_Postgres_ReturnsNeutralVariablesInOrder() {
        // given
        CloudComposeServiceRenderer renderer = new PostgresCloudComposeServiceRenderer();

        // when
        SequencedMap<String, String> environment = renderer.applicationEnvironment();

        // then
        assertEquals(
            List.of(Map.entry("POSTGRES_HOST", "postgres"), Map.entry("POSTGRES_PORT", "\"5432\"")),
            List.copyOf(environment.entrySet())
        );
    }

    @Test
    @DisplayName("PostgreSQL — 외부 .env를 참조하는 JDBC 연결 정보를 만든다")
    void jdbcConnection_Postgres_ReturnsServiceDnsJdbcConnection() {
        // given
        CloudComposeServiceRenderer renderer = new PostgresCloudComposeServiceRenderer();

        // when
        Optional<JdbcConnection> connection = renderer.jdbcConnection();

        // then
        assertEquals(
            Optional.of(new JdbcConnection(
                "\"jdbc:postgresql://postgres:5432/${POSTGRES_DB:?외부 .env에 설정 필요}\"",
                "\"${POSTGRES_USER:?외부 .env에 설정 필요}\"",
                "\"${POSTGRES_PASSWORD:?외부 .env에 설정 필요}\""
            )),
            connection
        );
    }

    @Test
    @DisplayName("Redis — 서비스 DNS 기준 REDIS_HOST/PORT/PASSWORD를 순서대로 만든다")
    void applicationEnvironment_Redis_ReturnsNeutralVariablesInOrder() {
        // given
        CloudComposeServiceRenderer renderer = new RedisCloudComposeServiceRenderer();

        // when
        SequencedMap<String, String> environment = renderer.applicationEnvironment();

        // then
        assertEquals(
            List.of(
                Map.entry("REDIS_HOST", "redis"),
                Map.entry("REDIS_PORT", "\"6379\""),
                Map.entry("REDIS_PASSWORD", "\"${REDIS_PASSWORD:?외부 .env에 설정 필요}\"")
            ),
            List.copyOf(environment.entrySet())
        );
    }

    @Test
    @DisplayName("Redis — DataSource 대상이 아니라 JDBC 연결 정보가 없다")
    void jdbcConnection_Redis_ReturnsEmpty() {
        // given
        CloudComposeServiceRenderer renderer = new RedisCloudComposeServiceRenderer();

        // when
        Optional<JdbcConnection> connection = renderer.jdbcConnection();

        // then
        assertTrue(connection.isEmpty());
    }
}
