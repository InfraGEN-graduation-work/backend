package com.infragen.infragen.domain.generation.generator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

class SpringBootApplicationEnvMapperTest {

    private final SpringBootApplicationEnvMapper mapper = new SpringBootApplicationEnvMapper();

    @Test
    @DisplayName("Spring Boot 앱 타입을 지원한다")
    void supportsSpringBoot() {
        assertEquals(ComponentType.SPRING_BOOT, mapper.getApplicationType());
    }

    @Test
    @DisplayName("관계형 DB가 하나면 구성요소로 JDBC URL을 조립해 DataSource 변수를 URL, USERNAME, PASSWORD 순서로 만든다")
    void databaseEnvironment_singleRelational_buildsDatasourceVariables() {
        // given
        var connections = connections(ComponentType.POSTGRESQL, relational("postgresql"));

        // when
        var environment = mapper.databaseEnvironment(connections);

        // then
        assertEquals(
            List.of("SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD"),
            List.copyOf(environment.keySet())
        );
        assertEquals(
            List.of("jdbc:postgresql://localhost:5432/db", "user", "pw"),
            List.copyOf(environment.values())
        );
    }

    @Test
    @DisplayName("관계형 DB가 둘 이상이면 DataSource 변수를 만들지 않는다")
    void databaseEnvironment_multipleRelational_returnsEmpty() {
        // given
        var connections = connections(
            ComponentType.MYSQL, relational("mysql"),
            ComponentType.POSTGRESQL, relational("postgresql")
        );

        // when
        var environment = mapper.databaseEnvironment(connections);

        // then
        assertTrue(environment.isEmpty());
    }

    @Test
    @DisplayName("비관계형 DB는 관계형 DB 개수에 세지 않는다")
    void databaseEnvironment_relationalWithMongo_ignoresMongoInCount() {
        // given
        var connections = connections(
            ComponentType.MYSQL, relational("mysql"),
            ComponentType.MONGODB, mongo(Optional.of("admin"))
        );

        // when
        var environment = mapper.databaseEnvironment(connections);
        var notice = mapper.multipleDatabaseNotice(connections, ".env");

        // then
        assertEquals("jdbc:mysql://localhost:5432/db", environment.get("SPRING_DATASOURCE_URL"));
        assertTrue(notice.isEmpty());
    }

    @Test
    @DisplayName("관계형 DB가 둘 이상일 때만 안내 주석을 만든다")
    void multipleDatabaseNotice_multipleRelational_returnsNotice() {
        // given
        var connections = connections(
            ComponentType.MYSQL, relational("mysql"),
            ComponentType.POSTGRESQL, relational("postgresql")
        );

        // when
        var notice = mapper.multipleDatabaseNotice(connections, ".env");

        // then
        assertEquals("""
            # 애플리케이션에 데이터베이스가 2개 이상 연결되어 SPRING_DATASOURCE_*를 생성하지 않았습니다.
            # DataSource를 직접 설정하고 .env의 DB별 접속 변수를 사용하세요.
            """, notice.orElseThrow());
    }

    @Test
    @DisplayName("안내 주석에 접속 변수 위치 이름을 넣는다")
    void multipleDatabaseNotice_envSource_isIncludedInNotice() {
        // given
        var connections = connections(
            ComponentType.MYSQL, relational("mysql"),
            ComponentType.POSTGRESQL, relational("postgresql")
        );

        // when
        var notice = mapper.multipleDatabaseNotice(connections, "app environment");

        // then
        assertTrue(notice.orElseThrow().contains("app environment의 DB별 접속 변수"));
    }

    @Test
    @DisplayName("DB가 없으면 변수도 안내도 만들지 않는다")
    void databaseEnvironment_noConnections_returnsEmpty() {
        // given
        SequencedMap<ComponentType, DatabaseConnection> connections = new LinkedHashMap<>();

        // when
        var environment = mapper.databaseEnvironment(connections);
        var notice = mapper.multipleDatabaseNotice(connections, ".env");

        // then
        assertTrue(environment.isEmpty());
        assertTrue(notice.isEmpty());
    }

    @Test
    @DisplayName("MongoDB 하나면 SPRING_MONGODB 변수를 호스트, 포트, DB, 계정, 비밀번호, 인증 DB 순서로 만든다")
    void databaseEnvironment_singleMongo_buildsMongoVariablesInOrder() {
        // given
        var connections = connections(ComponentType.MONGODB, mongo(Optional.of("admin")));

        // when
        var environment = mapper.databaseEnvironment(connections);

        // then
        assertEquals(
            List.of(
                "SPRING_MONGODB_HOST",
                "SPRING_MONGODB_PORT",
                "SPRING_MONGODB_DATABASE",
                "SPRING_MONGODB_USERNAME",
                "SPRING_MONGODB_PASSWORD",
                "SPRING_MONGODB_AUTHENTICATION_DATABASE"
            ),
            List.copyOf(environment.keySet())
        );
        assertEquals(
            List.of("localhost", "27017", "db", "user", "pw", "admin"),
            List.copyOf(environment.values())
        );
    }

    @Test
    @DisplayName("인증 DB가 없으면 SPRING_MONGODB_AUTHENTICATION_DATABASE를 만들지 않는다")
    void databaseEnvironment_mongoWithoutAuthenticationDatabase_omitsIt() {
        // given
        var connections = connections(ComponentType.MONGODB, mongo(Optional.empty()));

        // when
        var environment = mapper.databaseEnvironment(connections);

        // then
        assertFalse(environment.containsKey("SPRING_MONGODB_AUTHENTICATION_DATABASE"));
    }

    @Test
    @DisplayName("관계형 DB와 MongoDB가 함께 있으면 SPRING_DATASOURCE 다음에 SPRING_MONGODB 변수를 만든다")
    void databaseEnvironment_relationalAndMongo_buildsBoth() {
        // given
        var connections = connections(
            ComponentType.MYSQL, relational("mysql"),
            ComponentType.MONGODB, mongo(Optional.of("admin"))
        );

        // when
        var environment = mapper.databaseEnvironment(connections);

        // then
        assertEquals("SPRING_DATASOURCE_URL", environment.firstEntry().getKey());
        assertEquals("admin", environment.get("SPRING_MONGODB_AUTHENTICATION_DATABASE"));
    }

    @Test
    @DisplayName("규칙이 없는 비관계형 DB 종류는 조용히 무시하지 않고 INVALID_COMPONENT_STATE로 거부한다")
    void databaseEnvironment_unmappedNonRelationalType_throwsGenerationException() {
        // given
        var connections = connections(ComponentType.REDIS, new DatabaseConnection(
            "redis", false, "localhost", "6379", "0", "user", "pw"));

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> mapper.databaseEnvironment(connections)
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
    }

    private SequencedMap<ComponentType, DatabaseConnection> connections(
        ComponentType type, DatabaseConnection connection
    ) {
        SequencedMap<ComponentType, DatabaseConnection> connections = new LinkedHashMap<>();
        connections.put(type, connection);
        return connections;
    }

    private SequencedMap<ComponentType, DatabaseConnection> connections(
        ComponentType firstType, DatabaseConnection first,
        ComponentType secondType, DatabaseConnection second
    ) {
        SequencedMap<ComponentType, DatabaseConnection> connections = connections(firstType, first);
        connections.put(secondType, second);
        return connections;
    }

    private DatabaseConnection relational(String scheme) {
        return new DatabaseConnection(scheme, true, "localhost", "5432", "db", "user", "pw");
    }

    private DatabaseConnection mongo(Optional<String> authenticationDatabase) {
        return new DatabaseConnection(
            "mongodb", false, "localhost", "27017", "db", "user", "pw", authenticationDatabase);
    }
}
