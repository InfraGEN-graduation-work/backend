package com.infragen.infragen.domain.generation.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.infragen.infragen.domain.generation.generator.compose.RedisComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.compose.RedisHostAppEnvContributor;
import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLEnvComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
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
            List.of(new MysqlComposeServiceRenderer(), new RedisComposeServiceRenderer()),
            List.of(new MysqlHostAppEnvContributor(), new RedisHostAppEnvContributor())
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
