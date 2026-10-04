package com.infragen.infragen.domain.generation.generator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
    @DisplayName("JDBC 접속 정보를 SPRING_DATASOURCE 변수로 URL, USERNAME, PASSWORD 순서로 매핑한다")
    void mapsDatasourceEnvironmentInOrder() {
        var environment = mapper.datasourceEnvironment("jdbc:mysql://localhost:3306/db", "user", "pw");

        assertEquals(
            List.of("SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD"),
            List.copyOf(environment.keySet())
        );
        assertEquals(
            List.of("jdbc:mysql://localhost:3306/db", "user", "pw"),
            List.copyOf(environment.values())
        );
    }

    @Test
    @DisplayName("DB 여러 개 안내 주석에 접속 변수 위치 이름을 넣는다")
    void multipleDatabaseNoticeUsesEnvSource() {
        assertEquals("""
            # 애플리케이션에 데이터베이스가 2개 이상 연결되어 SPRING_DATASOURCE_*를 생성하지 않았습니다.
            # DataSource를 직접 설정하고 .env의 DB별 접속 변수를 사용하세요.
            """, mapper.multipleDatabaseNotice(".env"));
    }

    @Test
    @DisplayName("JDBC DB가 하나면 구성요소로 JDBC URL을 조립해 DataSource 변수를 만든다")
    void databaseEnvironment_singleJdbc_buildsDatasourceVariables() {
        // given
        var connections = List.of(connection("postgresql", true));

        // when
        var environment = mapper.databaseEnvironment(connections);

        // then
        assertEquals(
            List.of("jdbc:postgresql://localhost:5432/db", "user", "pw"),
            List.copyOf(environment.values())
        );
    }

    @Test
    @DisplayName("JDBC DB가 둘 이상이면 DataSource 변수를 만들지 않는다")
    void databaseEnvironment_multipleJdbc_returnsEmpty() {
        // given
        var connections = List.of(connection("mysql", true), connection("postgresql", true));

        // when
        var environment = mapper.databaseEnvironment(connections);

        // then
        assertTrue(environment.isEmpty());
    }

    @Test
    @DisplayName("비JDBC DB는 JDBC DB 개수에 세지 않는다")
    void databaseEnvironment_jdbcWithMongo_ignoresMongo() {
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
    @DisplayName("JDBC DB가 둘 이상일 때만 안내 주석을 만든다")
    void multipleDatabaseNotice_multipleJdbc_returnsNotice() {
        // given
        var connections = List.of(connection("mysql", true), connection("postgresql", true));

        // when
        var notice = mapper.multipleDatabaseNotice(connections, ".env");

        // then
        assertEquals(mapper.multipleDatabaseNotice(".env"), notice.orElseThrow());
    }

    @Test
    @DisplayName("JDBC DB가 없으면 변수도 안내도 만들지 않는다")
    void databaseEnvironment_noJdbc_returnsEmpty() {
        // given
        List<DatabaseConnection> connections = List.of();

        // when
        var environment = mapper.databaseEnvironment(connections);
        var notice = mapper.multipleDatabaseNotice(connections, ".env");

        // then
        assertTrue(environment.isEmpty());
        assertTrue(notice.isEmpty());
    }

    private DatabaseConnection connection(String scheme, boolean jdbc) {
        return new DatabaseConnection(scheme, jdbc, "localhost", "5432", "db", "user", "pw");
    }
}
