package com.infragen.infragen.domain.generation.generator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.global.enums.ComponentType;

class SpringBootApplicationEnvMapperTest {

    private final SpringBootApplicationEnvMapper mapper = new SpringBootApplicationEnvMapper();

    @Test
    @DisplayName("Spring Boot 앱 타입을 지원한다")
    void supportsSpringBoot() {
        assertEquals(ComponentType.SPRING_BOOT, mapper.getApplicationType());
    }

    @Test
    @DisplayName("관계형 DB가 하나면 구성요소로 JDBC URL을 조립해 DataSource 변수를 만든다")
    void databaseEnvironment_singleRelational_buildsDatasourceVariables() {
        // given
        var connections = List.of(connection("postgresql", true));

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
        var connections = List.of(connection("mysql", true), connection("postgresql", true));

        // when
        var environment = mapper.databaseEnvironment(connections);

        // then
        assertTrue(environment.isEmpty());
    }

    @Test
    @DisplayName("비관계형 DB는 관계형 DB 개수에 세지 않는다")
    void databaseEnvironment_relationalWithMongo_ignoresMongo() {
        // given
        var connections = List.of(connection("mysql", true), connection("mongodb", false));

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
        var connections = List.of(connection("mysql", true), connection("postgresql", true));

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
        var connections = List.of(connection("mysql", true), connection("postgresql", true));

        // when
        var notice = mapper.multipleDatabaseNotice(connections, "app environment");

        // then
        assertTrue(notice.orElseThrow().contains("app environment의 DB별 접속 변수"));
    }

    @Test
    @DisplayName("관계형 DB가 없으면 변수도 안내도 만들지 않는다")
    void databaseEnvironment_noRelational_returnsEmpty() {
        // given
        List<DatabaseConnection> connections = List.of();

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
        var connections = List.of(mongoConnection(Optional.of("admin")));

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
        var connections = List.of(mongoConnection(Optional.empty()));

        // when
        var environment = mapper.databaseEnvironment(connections);

        // then
        assertFalse(environment.containsKey("SPRING_MONGODB_AUTHENTICATION_DATABASE"));
    }

    @Test
    @DisplayName("관계형 DB와 MongoDB가 함께 있으면 SPRING_DATASOURCE 다음에 SPRING_MONGODB 변수를 만든다")
    void databaseEnvironment_relationalAndMongo_buildsBoth() {
        // given
        var connections = List.of(connection("mysql", true), mongoConnection(Optional.of("admin")));

        // when
        var environment = mapper.databaseEnvironment(connections);

        // then
        assertEquals("SPRING_DATASOURCE_URL", environment.firstEntry().getKey());
        assertEquals("admin", environment.get("SPRING_MONGODB_AUTHENTICATION_DATABASE"));
    }

    private DatabaseConnection connection(String scheme, boolean relational) {
        return new DatabaseConnection(scheme, relational, "localhost", "5432", "db", "user", "pw");
    }
    private DatabaseConnection mongoConnection(Optional<String> authenticationDatabase) {
        return new DatabaseConnection(
            "mongodb", false, "localhost", "27017", "db", "user", "pw", authenticationDatabase);
    }
}
