package com.infragen.infragen.domain.generation.generator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.global.enums.ComponentType;

class NodeJsApplicationEnvMapperTest {

    private final NodeJsApplicationEnvMapper mapper = new NodeJsApplicationEnvMapper();

    @Test
    @DisplayName("Node.js 앱 타입을 지원한다")
    void getApplicationType_returnsNodeJs() {
        // when
        ComponentType applicationType = mapper.getApplicationType();

        // then
        assertEquals(ComponentType.NODE_JS, applicationType);
    }

    @Test
    @DisplayName("관계형 DB가 하나면 프레임워크 변수를 만들지 않는다")
    void databaseEnvironment_singleRelational_returnsEmpty() {
        // given
        var connections = connections(ComponentType.MYSQL, relational("mysql"));

        // when
        var environment = mapper.databaseEnvironment(connections);

        // then
        assertTrue(environment.isEmpty());
    }

    @Test
    @DisplayName("MongoDB 앱 연결도 프레임워크 변수를 만들지 않는다")
    void databaseEnvironment_mongo_returnsEmpty() {
        // given
        var connections = connections(ComponentType.MONGODB, mongo());

        // when
        var environment = mapper.databaseEnvironment(connections);

        // then
        assertTrue(environment.isEmpty());
    }

    @Test
    @DisplayName("관계형 DB가 둘 이상이어도 프레임워크 변수를 만들지 않는다")
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
    @DisplayName("DB가 둘 이상이어도 안내 주석을 만들지 않는다")
    void multipleDatabaseNotice_multipleRelational_returnsEmpty() {
        // given
        var connections = connections(
            ComponentType.MYSQL, relational("mysql"),
            ComponentType.POSTGRESQL, relational("postgresql")
        );

        // when
        Optional<String> notice = mapper.multipleDatabaseNotice(connections, ".env");

        // then
        assertFalse(notice.isPresent());
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
        return new DatabaseConnection(scheme, true, "localhost", "3306", "db", "user", "pw");
    }

    private DatabaseConnection mongo() {
        return new DatabaseConnection("mongodb", false, "localhost", "27017", "db", "user", "pw");
    }
}
