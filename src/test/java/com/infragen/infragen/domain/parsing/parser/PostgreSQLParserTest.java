package com.infragen.infragen.domain.parsing.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.ObjectMapper;

@DisplayName("PostgreSQL 파서")
class PostgreSQLParserTest {

    private final PostgreSQLParser postgreSQLParser = new PostgreSQLParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("유효한 PostgreSQL 노드 — 속성과 접속 정보 파싱 성공")
    void parse_ValidNode_ReturnsPostgreSQLComponent() {
        // given
        Map<String, Object> properties = validProperties();

        // when
        PostgreSQLComponent component = (PostgreSQLComponent) parse(properties);

        // then
        assertAll(
            () -> assertEquals("node-1", component.getNodeId()),
            () -> assertEquals(ComponentType.POSTGRESQL, component.getComponentType()),
            () -> assertEquals("postgres:17", component.getImageVersion()),
            () -> assertEquals("postgres", component.getContainerName()),
            () -> assertEquals("postgres_data", component.getVolumeName()),
            () -> assertEquals(5432, component.getPort()),
            () -> assertEquals("appdb", component.getEnv().getDatabaseName()),
            () -> assertEquals("appuser", component.getEnv().getUsername()),
            () -> assertEquals("password12", component.getEnv().getPassword())
        );
    }

    @Test
    @DisplayName("선택 속성 누락 — containerName, volumeName 빈 문자열")
    void parse_MissingOptionalProperties_UsesEmptyString() {
        // given
        Map<String, Object> properties = validProperties();
        properties.remove("containerName");
        properties.remove("volumeName");

        // when
        PostgreSQLComponent component = (PostgreSQLComponent) parse(properties);

        // then
        assertAll(
            () -> assertEquals("", component.getContainerName()),
            () -> assertEquals("", component.getVolumeName())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "app-db", "app db"})
    @DisplayName("databaseName 형식 오류 — INVALID_DB_NAME")
    void parse_InvalidDatabaseName_ThrowsParsingException(String databaseName) {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).put("databaseName", databaseName);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.INVALID_DB_NAME, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short12"})
    @DisplayName("password 8자 미만 — INVALID_DB_PASSWORD")
    void parse_ShortPassword_ThrowsParsingException(String password) {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).put("password", password);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.INVALID_DB_PASSWORD, exception.getCode());
    }

    @Test
    @DisplayName("password 누락 — INVALID_DB_PASSWORD")
    void parse_MissingPassword_ThrowsParsingException() {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).remove("password");

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.INVALID_DB_PASSWORD, exception.getCode());
    }

    @Test
    @DisplayName("rootPassword만 있고 password 누락 — INVALID_DB_PASSWORD")
    void parse_RootPasswordWithoutPassword_ThrowsParsingException() {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).remove("password");
        env(properties).put("rootPassword", "rootpass12");

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.INVALID_DB_PASSWORD, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("imageVersion 빈 값 — MISSING_POSTGRES_IMAGE_VERSION")
    void parse_BlankImageVersion_ThrowsParsingException(String imageVersion) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("imageVersion", imageVersion);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.MISSING_POSTGRES_IMAGE_VERSION, exception.getCode());
    }

    @Test
    @DisplayName("imageVersion 누락 — MISSING_POSTGRES_IMAGE_VERSION")
    void parse_MissingImageVersion_ThrowsParsingException() {
        // given
        Map<String, Object> properties = validProperties();
        properties.remove("imageVersion");

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.MISSING_POSTGRES_IMAGE_VERSION, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("username 빈 값 — MISSING_POSTGRES_USERNAME")
    void parse_BlankUsername_ThrowsParsingException(String username) {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).put("username", username);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.MISSING_POSTGRES_USERNAME, exception.getCode());
    }

    private BaseComponent parse(Map<String, Object> properties) {
        NodeDTO node = new NodeDTO(
            "node-1",
            "POSTGRESQL",
            100f,
            200f,
            properties
        );

        return postgreSQLParser.parse(
            node,
            objectMapper.valueToTree(properties),
            5432
        );
    }

    private static Map<String, Object> validProperties() {
        Map<String, Object> env = new HashMap<>();
        env.put("databaseName", "appdb");
        env.put("username", "appuser");
        env.put("password", "password12");

        Map<String, Object> properties = new HashMap<>();
        properties.put("imageVersion", "postgres:17");
        properties.put("containerName", "postgres");
        properties.put("volumeName", "postgres_data");
        properties.put("port", 5432);
        properties.put("env", env);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> env(
        Map<String, Object> properties
    ) {
        return (Map<String, Object>) properties.get("env");
    }
}
