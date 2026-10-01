package com.infragen.infragen.domain.generation.generator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.generation.generator.compose.MysqlComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.compose.MysqlHostAppEnvContributor;
import com.infragen.infragen.domain.generation.generator.compose.PostgresComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.compose.PostgresHostAppEnvContributor;
import com.infragen.infragen.domain.generation.generator.compose.RedisComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.compose.RedisHostAppEnvContributor;
import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLEnvComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLEnvComponent;
import com.infragen.infragen.domain.parsing.dto.response.RedisComponent;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;

@DisplayName("Docker Compose IaC 생성기")
class DockerComposeIaCGeneratorTest {
    private static final String EXPECTED_COMPOSE = """
        services:
          mysql:
            image: mysql:8.0
            container_name: mysql
            ports:
              - "3306:3306"
            volumes:
              - mysql_data:/var/lib/mysql
            env_file:
              - .env
            environment:
              MYSQL_DATABASE: ${MYSQL_DATABASE}
              MYSQL_USER: ${MYSQL_USER}
              MYSQL_PASSWORD: ${MYSQL_PASSWORD}
              MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
              TZ: Asia/Seoul

        volumes:
          mysql_data:
        """;

    private static final String EXPECTED_ENV = """
        # InfraGEN generated environment variables
        # 민감한 정보는 이 파일에만 저장하세요. 버전 관리에 커밋하지 마세요.

        MYSQL_DATABASE=appdb
        MYSQL_USER=user
        MYSQL_PASSWORD=userpass12
        MYSQL_ROOT_PASSWORD=rootpass12
        SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/appdb
        SPRING_DATASOURCE_USERNAME=user
        SPRING_DATASOURCE_PASSWORD=userpass12
        MYSQL_HOST=localhost
        MYSQL_PORT=3306
        """;

    private DockerComposeIaCGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new DockerComposeIaCGenerator(
            List.of(
                new MysqlComposeServiceRenderer(),
                new RedisComposeServiceRenderer(),
                new PostgresComposeServiceRenderer()
            ),
            List.of(
                new MysqlHostAppEnvContributor(),
                new RedisHostAppEnvContributor(),
                new PostgresHostAppEnvContributor()
            )
        );
    }

    @Test
    @DisplayName("OutputFormat — DOCKER_COMPOSE")
    void getOutputFormat_ReturnsDockerCompose() {
        assertEquals(OutputFormat.DOCKER_COMPOSE, generator.getOutputFormat());
    }

    @Test
    @DisplayName("LOCAL_DEV golden — compose MySQL만, .env localhost JDBC")
    void generate_LocalDevMySqlAndSpringBoot_MatchesGolden() {
        ParsingResultDTO parsingResult = localDevParsingResult();

        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult);

        assertEquals(2, bundle.files().size());
        assertEquals(EXPECTED_COMPOSE, fileContent(bundle, "docker-compose.yml"));
        assertEquals(EXPECTED_ENV, fileContent(bundle, ".env"));
        assertFalse(fileContent(bundle, "docker-compose.yml").contains("eclipse-temurin"));
    }

    @Test
    @DisplayName("MySQL env 누락 — GENERATION400_2")
    void generate_MysqlEnvMissing_ThrowsGenerationException() {
        ParsingResultDTO parsingResult = localDevParsingResultWithMysqlEnv(null);

        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> generator.generate(parsingResult)
        );

        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
    }

    @Test
    @DisplayName("LOCAL_DEV golden — Redis와 .env localhost password 생성")
    void generate_LocalDevRedisAndSpringBoot_MatchesGolden() {
        // given
        ParsingResultDTO parsingResult = redisLocalDevParsingResult();

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult);

        // then
        assertEquals(2, bundle.files().size());
        assertEquals("""
            services:
              redis:
                image: redis:7.4
                container_name: redis
                ports:
                  - "6379:6379"
                volumes:
                  - redis_data:/data
                env_file:
                  - .env
                command: redis-server --requirepass ${REDIS_PASSWORD} --appendonly yes

            volumes:
              redis_data:
            """, fileContent(bundle, "docker-compose.yml"));
        assertEquals("""
            # InfraGEN generated environment variables
            # 민감한 정보는 이 파일에만 저장하세요. 버전 관리에 커밋하지 마세요.

            REDIS_HOST=localhost
            REDIS_PORT=6379
            REDIS_PASSWORD=redis-password
            """, fileContent(bundle, ".env"));
    }

    @Test
    @DisplayName("LOCAL_DEV golden — PostgreSQL 단일 DB면 PGDATA 고정과 Spring DataSource 생성")
    void generate_LocalDevPostgresAndSpringBoot_MatchesGolden() {
        // given
        ParsingResultDTO parsingResult = parsingResult(
            List.of(postgresComponent(validPostgresEnv()), springBootComponent()),
            List.of(edge("pg-1", "node-2"))
        );

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult);

        // then
        assertAll(
            () -> assertEquals("""
                services:
                  postgres:
                    image: postgres:17
                    container_name: postgres
                    ports:
                      - "5433:5432"
                    volumes:
                      - pg_data:/var/lib/postgresql/data
                    env_file:
                      - .env
                    environment:
                      POSTGRES_DB: ${POSTGRES_DB}
                      POSTGRES_USER: ${POSTGRES_USER}
                      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
                      PGDATA: /var/lib/postgresql/data
                      TZ: Asia/Seoul

                volumes:
                  pg_data:
                """, fileContent(bundle, "docker-compose.yml")),
            () -> assertEquals("""
                # InfraGEN generated environment variables
                # 민감한 정보는 이 파일에만 저장하세요. 버전 관리에 커밋하지 마세요.

                POSTGRES_DB=pgdb
                POSTGRES_USER=pguser
                POSTGRES_PASSWORD=pgpass1234
                SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/pgdb
                SPRING_DATASOURCE_USERNAME=pguser
                SPRING_DATASOURCE_PASSWORD=pgpass1234
                POSTGRES_HOST=localhost
                POSTGRES_PORT=5433
                """, fileContent(bundle, ".env"))
        );
    }

    @Test
    @DisplayName("PostgreSQL env 누락 — GENERATION400_2")
    void generate_PostgresEnvMissing_ThrowsGenerationException() {
        // given
        ParsingResultDTO parsingResult = parsingResult(
            List.of(postgresComponent(null), springBootComponent()),
            List.of(edge("pg-1", "node-2"))
        );

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> generator.generate(parsingResult)
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
    }

    @Test
    @DisplayName("MySQL + PostgreSQL — PostgreSQL 접속 변수는 생성, PostgreSQL DataSource URL은 미생성")
    void generate_LocalDevMysqlAndPostgres_SkipsPostgresDataSource() {
        // given
        ParsingResultDTO parsingResult = parsingResult(
            List.of(
                mysqlComponent(),
                postgresComponent(validPostgresEnv()),
                springBootComponent()
            ),
            List.of(edge("node-1", "node-2"), edge("pg-1", "node-2"))
        );

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult);

        // then
        String compose = fileContent(bundle, "docker-compose.yml");
        String env = fileContent(bundle, ".env");
        assertAll(
            () -> assertTrue(compose.contains("  mysql:\n")),
            () -> assertTrue(compose.contains("  postgres:\n")),
            () -> assertTrue(env.contains("POSTGRES_HOST=localhost\n")),
            () -> assertTrue(env.contains("POSTGRES_PORT=5433\n")),
            () -> assertFalse(env.contains("jdbc:postgresql"))
        );
    }

    @Test
    @DisplayName("PostgreSQL이 앱과 연결되지 않으면 서비스만 생성하고 호스트 접속 변수는 미생성")
    void generate_UnconnectedPostgres_RendersServiceWithoutHostEnv() {
        // given
        ParsingResultDTO parsingResult = parsingResult(
            List.of(postgresComponent(validPostgresEnv()), springBootComponent()),
            List.of()
        );

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult);

        // then
        String env = fileContent(bundle, ".env");
        assertAll(
            () -> assertTrue(fileContent(bundle, "docker-compose.yml").contains("  postgres:\n")),
            () -> assertFalse(env.contains("POSTGRES_HOST")),
            () -> assertFalse(env.contains("SPRING_DATASOURCE_URL"))
        );
    }

    private static PostgreSQLEnvComponent validPostgresEnv() {
        return PostgreSQLEnvComponent.builder()
            .databaseName("pgdb")
            .username("pguser")
            .password("pgpass1234")
            .build();
    }

    private static PostgreSQLComponent postgresComponent(PostgreSQLEnvComponent env) {
        return PostgreSQLComponent.builder()
            .id("pg-1")
            .posX(100f)
            .posY(300f)
            .imageVersion("postgres:17")
            .containerName("postgres")
            .volumeName("pg_data")
            .port(5433)
            .env(env)
            .build();
    }

    private static MySQLComponent mysqlComponent() {
        return MySQLComponent.builder()
            .id("node-1")
            .posX(100f)
            .posY(200f)
            .imageVersion("mysql:8.0")
            .containerName("mysql")
            .volumeName("mysql_data")
            .port(3306)
            .env(MySQLEnvComponent.builder()
                .databaseName("appdb")
                .username("user")
                .userPassword("userpass12")
                .rootPassword("rootpass12")
                .build())
            .build();
    }

    private static SpringBootComponent springBootComponent() {
        return SpringBootComponent.builder()
            .id("node-2")
            .posX(400f)
            .posY(200f)
            .name("app")
            .port(8080)
            .javaVersion("17")
            .containerName("spring-app")
            .build();
    }

    private static EdgeDTO edge(String sourceNodeId, String targetNodeId) {
        EdgeDTO edge = new EdgeDTO();
        edge.setSourceNodeId(sourceNodeId);
        edge.setTargetNodeId(targetNodeId);
        return edge;
    }

    private static ParsingResultDTO parsingResult(
        List<BaseComponent> components,
        List<EdgeDTO> edges
    ) {
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        parsingResult.setProjectId(1L);
        parsingResult.setComponents(components);
        parsingResult.setEdges(edges);
        return parsingResult;
    }

    private static ParsingResultDTO localDevParsingResult() {
        return localDevParsingResultWithMysqlEnv(MySQLEnvComponent.builder()
            .databaseName("appdb")
            .username("user")
            .userPassword("userpass12")
            .rootPassword("rootpass12")
            .build());
    }

    private static ParsingResultDTO localDevParsingResultWithMysqlEnv(MySQLEnvComponent env) {
        MySQLComponent mysql = MySQLComponent.builder()
            .id("node-1")
            .posX(100f)
            .posY(200f)
            .imageVersion("mysql:8.0")
            .containerName("mysql")
            .volumeName("mysql_data")
            .port(3306)
            .env(env)
            .build();

        SpringBootComponent springBoot = SpringBootComponent.builder()
            .id("node-2")
            .posX(400f)
            .posY(200f)
            .name("app")
            .port(8080)
            .javaVersion("17")
            .containerName("spring-app")
            .build();

        EdgeDTO edge = new EdgeDTO();
        edge.setSourceNodeId("node-1");
        edge.setTargetNodeId("node-2");

        ParsingResultDTO parsingResult = new ParsingResultDTO();
        parsingResult.setProjectId(1L);
        parsingResult.setComponents(List.of(mysql, springBoot));
        parsingResult.setEdges(List.of(edge));
        return parsingResult;
    }

    private static ParsingResultDTO redisLocalDevParsingResult() {
        RedisComponent redis = RedisComponent.builder()
            .id("redis-1")
            .posX(100f)
            .posY(200f)
            .imageVersion("redis:7.4")
            .containerName("redis")
            .volumeName("redis_data")
            .port(6379)
            .password("redis-password")
            .build();

        SpringBootComponent springBoot = SpringBootComponent.builder()
            .id("node-2")
            .posX(400f)
            .posY(200f)
            .name("app")
            .port(8080)
            .javaVersion("17")
            .containerName("spring-app")
            .build();

        EdgeDTO edge = new EdgeDTO();
        edge.setSourceNodeId("redis-1");
        edge.setTargetNodeId("node-2");

        ParsingResultDTO parsingResult = new ParsingResultDTO();
        parsingResult.setProjectId(1L);
        parsingResult.setComponents(List.of(redis, springBoot));
        parsingResult.setEdges(List.of(edge));
        return parsingResult;
    }

    private static String fileContent(IaCFileDTO.BundleResDTO bundle, String fileName) {
        String scopedFileName = fileName.startsWith("local/")
            ? fileName
            : "local/" + fileName;
        return bundle.files().stream()
            .filter(file -> scopedFileName.equals(file.fileName()))
            .findFirst()
            .orElseThrow()
            .content();
    }
}
