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
import com.infragen.infragen.domain.generation.generator.application.SpringBootApplicationEnvMapper;
import com.infragen.infragen.domain.generation.generator.compose.MongoComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.compose.MongoHostAppEnvContributor;
import com.infragen.infragen.domain.generation.generator.compose.MysqlComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.compose.MysqlHostAppEnvContributor;
import com.infragen.infragen.domain.generation.generator.compose.PostgresComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.compose.PostgresHostAppEnvContributor;
import com.infragen.infragen.domain.generation.generator.compose.RedisComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.compose.RedisHostAppEnvContributor;
import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.MongoDBComponent;
import com.infragen.infragen.domain.parsing.dto.response.MongoDBEnvComponent;
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

    private static final String MYSQL_SERVICE_BLOCK = """
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
        """;

    private static final String POSTGRES_SERVICE_BLOCK = """
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
        """;

    private static final String REDIS_SERVICE_BLOCK = """
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
        """;

    private DockerComposeIaCGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new DockerComposeIaCGenerator(
            List.of(
                new MysqlComposeServiceRenderer(),
                new RedisComposeServiceRenderer(),
                new PostgresComposeServiceRenderer(),
                new MongoComposeServiceRenderer()
            ),
            List.of(
                new MysqlHostAppEnvContributor(),
                new RedisHostAppEnvContributor(),
                new PostgresHostAppEnvContributor(),
                new MongoHostAppEnvContributor()
            ),
            List.of(new SpringBootApplicationEnvMapper())
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
    @DisplayName("특수문자 비밀번호 — .env에 큰따옴표와 이스케이프로 출력하고 안전한 값은 그대로")
    void generate_SpecialCharacterPassword_EscapesEnvValues() {
        // given
        ParsingResultDTO parsingResult = localDevParsingResultWithMysqlEnv(MySQLEnvComponent.builder()
            .databaseName("appdb")
            .username("user")
            .userPassword("pa$word\"12\\")
            .rootPassword("rootpass12")
            .build());

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult);

        // then
        assertEquals("""
            # InfraGEN generated environment variables
            # 민감한 정보는 이 파일에만 저장하세요. 버전 관리에 커밋하지 마세요.

            MYSQL_DATABASE=appdb
            MYSQL_USER=user
            MYSQL_PASSWORD="pa$$word\\"12\\\\"
            MYSQL_ROOT_PASSWORD=rootpass12
            SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/appdb
            SPRING_DATASOURCE_USERNAME=user
            SPRING_DATASOURCE_PASSWORD="pa$$word\\"12\\\\"
            MYSQL_HOST=localhost
            MYSQL_PORT=3306
            """, fileContent(bundle, ".env"));
    }

    @Test
    @DisplayName("비밀번호에 개행이 있으면 생성 단계에서 GENERATION400_13으로 거부")
    void generate_PasswordWithLineBreak_ThrowsGenerationException() {
        // given
        ParsingResultDTO parsingResult = localDevParsingResultWithMysqlEnv(MySQLEnvComponent.builder()
            .databaseName("appdb")
            .username("user")
            .userPassword("userpass12\nINJECTED=1")
            .rootPassword("rootpass12")
            .build());

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> generator.generate(parsingResult)
        );

        // then
        assertEquals(IaCGenerationErrorCode.UNSUPPORTED_ENV_VALUE, exception.getCode());
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

            REDIS_PASSWORD=redis-password
            REDIS_HOST=localhost
            REDIS_PORT=6379
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
    @DisplayName("LOCAL_DEV golden — MongoDB 단일 DB면 SPRING_MONGODB_* 생성, admin 인증 DB 포함")
    void generate_LocalDevMongoAndSpringBoot_MatchesGolden() {
        // given
        ParsingResultDTO parsingResult = parsingResult(
            List.of(mongoComponent(validMongoEnv()), springBootComponent()),
            List.of(edge("mongo-1", "node-2"))
        );

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult);

        // then
        assertAll(
            () -> assertEquals("""
                services:
                  mongo:
                    image: mongo:8.0
                    container_name: mongo
                    ports:
                      - "27018:27017"
                    volumes:
                      - mongo_data:/data/db
                    env_file:
                      - .env
                    environment:
                      MONGO_INITDB_ROOT_USERNAME: ${MONGO_USER}
                      MONGO_INITDB_ROOT_PASSWORD: ${MONGO_PASSWORD}
                      MONGO_INITDB_DATABASE: ${MONGO_DATABASE}
                      TZ: Asia/Seoul

                volumes:
                  mongo_data:
                """, fileContent(bundle, "docker-compose.yml")),
            () -> assertEquals("""
                # InfraGEN generated environment variables
                # 민감한 정보는 이 파일에만 저장하세요. 버전 관리에 커밋하지 마세요.

                MONGO_DATABASE=mongodb_app
                MONGO_USER=mongouser
                MONGO_PASSWORD=mongopass12
                SPRING_MONGODB_HOST=localhost
                SPRING_MONGODB_PORT=27018
                SPRING_MONGODB_DATABASE=mongodb_app
                SPRING_MONGODB_USERNAME=mongouser
                SPRING_MONGODB_PASSWORD=mongopass12
                SPRING_MONGODB_AUTHENTICATION_DATABASE=admin
                MONGO_HOST=localhost
                MONGO_PORT=27018
                """, fileContent(bundle, ".env"))
        );
    }

    @Test
    @DisplayName("MySQL + MongoDB — SPRING_DATASOURCE_*와 SPRING_MONGODB_*를 함께 만들고 안내 주석은 없다")
    void generate_LocalDevMysqlAndMongo_BuildsBothWithoutNotice() {
        // given
        ParsingResultDTO parsingResult = parsingResult(
            List.of(mysqlComponent(), mongoComponent(validMongoEnv()), springBootComponent()),
            List.of(edge("node-1", "node-2"), edge("mongo-1", "node-2"))
        );

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult);

        // then
        String env = fileContent(bundle, ".env");
        assertAll(
            () -> assertTrue(env.contains("SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/appdb\n")),
            () -> assertTrue(env.contains("SPRING_MONGODB_HOST=localhost\n")),
            () -> assertTrue(env.contains("MYSQL_HOST=localhost\n")),
            () -> assertTrue(env.contains("MONGO_HOST=localhost\n")),
            () -> assertFalse(fileContent(bundle, "docker-compose.yml").startsWith("#"))
        );
    }

    @Test
    @DisplayName("MongoDB env 누락 — GENERATION400_2")
    void generate_MongoEnvMissing_ThrowsGenerationException() {
        // given
        ParsingResultDTO parsingResult = parsingResult(
            List.of(mongoComponent(null), springBootComponent()),
            List.of(edge("mongo-1", "node-2"))
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
    @DisplayName("MySQL + PostgreSQL — 타입별 접속 변수만 생성하고 SPRING_DATASOURCE_* 대신 안내 주석 추가")
    void generate_LocalDevMysqlAndPostgres_SkipsDataSourceAndAddsNotice() {
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
            () -> assertTrue(compose.startsWith("""
                # 애플리케이션에 데이터베이스가 2개 이상 연결되어 SPRING_DATASOURCE_*를 생성하지 않았습니다.
                # DataSource를 직접 설정하고 .env의 DB별 접속 변수를 사용하세요.
                services:
                """)),
            () -> assertTrue(compose.contains("  mysql:\n")),
            () -> assertTrue(compose.contains("  postgres:\n")),
            () -> assertTrue(env.contains("MYSQL_HOST=localhost\n")),
            () -> assertTrue(env.contains("MYSQL_PORT=3306\n")),
            () -> assertTrue(env.contains("POSTGRES_HOST=localhost\n")),
            () -> assertTrue(env.contains("POSTGRES_PORT=5433\n")),
            () -> assertFalse(env.contains("SPRING_DATASOURCE_"))
        );
    }

    @Test
    @DisplayName("LOCAL_DEV golden — MySQL + Redis면 MySQL DataSource와 Redis 접속 변수를 연결 순서대로 생성")
    void generate_LocalDevMysqlAndRedis_MatchesGolden() {
        // given
        ParsingResultDTO parsingResult = parsingResult(
            List.of(mysqlComponent(), redisComponent(), springBootComponent()),
            List.of(edge("node-1", "node-2"), edge("redis-1", "node-2"))
        );

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult);

        // then
        assertAll(
            () -> assertEquals("""
                services:
                """ + MYSQL_SERVICE_BLOCK + REDIS_SERVICE_BLOCK + """

                volumes:
                  mysql_data:
                  redis_data:
                """, fileContent(bundle, "docker-compose.yml")),
            () -> assertEquals("""
                # InfraGEN generated environment variables
                # 민감한 정보는 이 파일에만 저장하세요. 버전 관리에 커밋하지 마세요.

                MYSQL_DATABASE=appdb
                MYSQL_USER=user
                MYSQL_PASSWORD=userpass12
                MYSQL_ROOT_PASSWORD=rootpass12
                REDIS_PASSWORD=redis-password
                SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/appdb
                SPRING_DATASOURCE_USERNAME=user
                SPRING_DATASOURCE_PASSWORD=userpass12
                MYSQL_HOST=localhost
                MYSQL_PORT=3306
                REDIS_HOST=localhost
                REDIS_PORT=6379
                """, fileContent(bundle, ".env"))
        );
    }

    @Test
    @DisplayName("LOCAL_DEV — Redis가 먼저 연결돼도 DataSource 변수는 MySQL 중립 변수 바로 앞에 놓인다")
    void generate_LocalDevRedisBeforeMysql_PlacesDataSourceBeforeMysqlVariables() {
        // given
        ParsingResultDTO parsingResult = parsingResult(
            List.of(mysqlComponent(), redisComponent(), springBootComponent()),
            List.of(edge("redis-1", "node-2"), edge("node-1", "node-2"))
        );

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult);

        // then
        String env = fileContent(bundle, ".env");
        assertTrue(env.indexOf("REDIS_PASSWORD=") < env.indexOf("SPRING_DATASOURCE_URL="));
        assertTrue(env.indexOf("SPRING_DATASOURCE_PASSWORD=") < env.indexOf("MYSQL_HOST="));
    }

    @Test
    @DisplayName("LOCAL_DEV golden — MySQL + PostgreSQL이면 안내 주석과 타입별 접속 변수만 생성")
    void generate_LocalDevMysqlAndPostgres_MatchesGolden() {
        // given
        ParsingResultDTO parsingResult = parsingResult(
            List.of(mysqlComponent(), postgresComponent(validPostgresEnv()), springBootComponent()),
            List.of(edge("node-1", "node-2"), edge("pg-1", "node-2"))
        );

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult);

        // then
        assertAll(
            () -> assertEquals("""
                # 애플리케이션에 데이터베이스가 2개 이상 연결되어 SPRING_DATASOURCE_*를 생성하지 않았습니다.
                # DataSource를 직접 설정하고 .env의 DB별 접속 변수를 사용하세요.
                services:
                """ + MYSQL_SERVICE_BLOCK + POSTGRES_SERVICE_BLOCK + """

                volumes:
                  mysql_data:
                  pg_data:
                """, fileContent(bundle, "docker-compose.yml")),
            () -> assertEquals("""
                # InfraGEN generated environment variables
                # 민감한 정보는 이 파일에만 저장하세요. 버전 관리에 커밋하지 마세요.

                MYSQL_DATABASE=appdb
                MYSQL_USER=user
                MYSQL_PASSWORD=userpass12
                MYSQL_ROOT_PASSWORD=rootpass12
                POSTGRES_DB=pgdb
                POSTGRES_USER=pguser
                POSTGRES_PASSWORD=pgpass1234
                MYSQL_HOST=localhost
                MYSQL_PORT=3306
                POSTGRES_HOST=localhost
                POSTGRES_PORT=5433
                """, fileContent(bundle, ".env"))
        );
    }

    @Test
    @DisplayName("같은 MySQL→앱 edge 중복 — 단일 DB로 세어 SPRING_DATASOURCE_* 유지, 안내 주석 없음")
    void generate_DuplicateMysqlEdge_KeepsSingleDatabaseDataSource() {
        // given
        ParsingResultDTO parsingResult = parsingResult(
            List.of(mysqlComponent(), springBootComponent()),
            List.of(edge("node-1", "node-2"), edge("node-1", "node-2"))
        );

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult);

        // then
        assertAll(
            () -> assertEquals(EXPECTED_COMPOSE, fileContent(bundle, "docker-compose.yml")),
            () -> assertEquals(EXPECTED_ENV, fileContent(bundle, ".env"))
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

    @Test
    @DisplayName("Redis만 있고 앱과 연결되지 않아도 REDIS_PASSWORD는 생성하고 호스트 접속 변수는 미생성")
    void generate_UnconnectedRedis_RendersPasswordWithoutHostEnv() {
        // given
        ParsingResultDTO parsingResult = parsingResult(List.of(redisComponent()), List.of());

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult);

        // then
        String env = fileContent(bundle, ".env");
        assertAll(
            () -> assertTrue(env.contains("REDIS_PASSWORD=redis-password\n")),
            () -> assertFalse(env.contains("REDIS_HOST")),
            () -> assertFalse(env.contains("REDIS_PORT"))
        );
    }

    @Test
    @DisplayName("앱이 다른 DB에만 연결되고 Redis가 미연결이어도 REDIS_PASSWORD는 생성")
    void generate_UnconnectedRedisWithMysqlApp_RendersPassword() {
        // given
        ParsingResultDTO parsingResult = parsingResult(
            List.of(mysqlComponent(), redisComponent(), springBootComponent()),
            List.of(edge("node-1", "node-2"))
        );

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult);

        // then
        String env = fileContent(bundle, ".env");
        assertAll(
            () -> assertTrue(env.contains("REDIS_PASSWORD=redis-password\n")),
            () -> assertTrue(env.contains("SPRING_DATASOURCE_URL=")),
            () -> assertFalse(env.contains("REDIS_HOST"))
        );
    }

    @Test
    @DisplayName("Redis password 누락 — GENERATION400_2")
    void generate_RedisPasswordBlank_ThrowsGenerationException() {
        // given
        RedisComponent redis = RedisComponent.builder()
            .id("redis-1")
            .imageVersion("redis:7.4")
            .containerName("redis")
            .port(6379)
            .password(" ")
            .build();
        ParsingResultDTO parsingResult = parsingResult(List.of(redis), List.of());

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> generator.generate(parsingResult)
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
    }

    private static MongoDBEnvComponent validMongoEnv() {
        return MongoDBEnvComponent.builder()
            .databaseName("mongodb_app")
            .username("mongouser")
            .password("mongopass12")
            .build();
    }

    private static MongoDBComponent mongoComponent(MongoDBEnvComponent env) {
        return MongoDBComponent.builder()
            .id("mongo-1")
            .posX(100f)
            .posY(300f)
            .imageVersion("mongo:8.0")
            .containerName("mongo")
            .volumeName("mongo_data")
            .port(27018)
            .env(env)
            .build();
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

    private static RedisComponent redisComponent() {
        return RedisComponent.builder()
            .id("redis-1")
            .posX(100f)
            .posY(400f)
            .imageVersion("redis:7.4")
            .containerName("redis")
            .volumeName("redis_data")
            .port(6379)
            .password("redis-password")
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
