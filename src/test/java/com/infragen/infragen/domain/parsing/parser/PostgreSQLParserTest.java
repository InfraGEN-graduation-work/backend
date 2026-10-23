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

    @ParameterizedTest
    @ValueSource(strings = {"a\"b", "a\\b", "a$b", "a\nb", "a\rb", "a\0b"})
    @DisplayName("username 금지 문자 — UNSUPPORTED_ENV_VALUE_CHARACTER")
    void parse_UsernameWithForbiddenCharacter_ThrowsParsingException(String value) {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).put("username", value);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(
            ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcd\"efgh", "abcd\\efgh", "abcd$efgh", "abcd\nefgh", "abcd\refgh", "abcd\0efgh"})
    @DisplayName("password 금지 문자 — UNSUPPORTED_ENV_VALUE_CHARACTER")
    void parse_PasswordWithForbiddenCharacter_ThrowsParsingException(String value) {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).put("password", value);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(
            ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "postgres", "postgres:17-alpine", "ghcr.io/org/postgres:17", "localhost:5000/postgres:17",
        "postgres@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    })
    @DisplayName("Docker 이미지 참조 형식 — 파싱 성공")
    void parse_ValidImageVersion_ReturnsComponent(String imageVersion) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("imageVersion", imageVersion);

        // when
        PostgreSQLComponent component = (PostgreSQLComponent) parse(properties);

        // then
        assertEquals(imageVersion, component.getImageVersion());
    }

    @Test
    @DisplayName("imageVersion 앞뒤 공백 — 제거해서 저장")
    void parse_ImageVersionWithSurroundingSpaces_TrimsImageVersion() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("imageVersion", " postgres:17 ");

        // when
        PostgreSQLComponent component = (PostgreSQLComponent) parse(properties);

        // then
        assertEquals("postgres:17", component.getImageVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "postgres:17\n    privileged: true", "postgres 17", "${IMAGE}", "postgres:",
        "postgres::17", "Postgres:17", "postgres@sha256:abc123"
    })
    @DisplayName("YAML 구조를 바꾸거나 이미지 참조 구조가 아닌 imageVersion — INVALID_POSTGRES_IMAGE_VERSION")
    void parse_InvalidImageVersion_ThrowsParsingException(String imageVersion) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("imageVersion", imageVersion);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(
            ParsingErrorCode.INVALID_POSTGRES_IMAGE_VERSION,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"postgres_data", "postgres-data.1", "Data1"})
    @DisplayName("Docker 볼륨 이름 규칙 — 파싱 성공")
    void parse_ValidVolumeName_ReturnsComponent(String volumeName) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("volumeName", volumeName);

        // when
        PostgreSQLComponent component = (PostgreSQLComponent) parse(properties);

        // then
        assertEquals(volumeName, component.getVolumeName());
    }

    @Test
    @DisplayName("volumeName 앞뒤 공백 — 제거해서 저장")
    void parse_VolumeNameWithSurroundingSpaces_TrimsVolumeName() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("volumeName", "  postgres_data  ");

        // when
        PostgreSQLComponent component = (PostgreSQLComponent) parse(properties);

        // then
        assertEquals("postgres_data", component.getVolumeName());
    }

    @Test
    @DisplayName("volumeName 공백뿐인 값 — 볼륨 없이 파싱")
    void parse_BlankVolumeName_ReturnsEmptyVolumeName() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("volumeName", "  ");

        // when
        PostgreSQLComponent component = (PostgreSQLComponent) parse(properties);

        // then
        assertEquals("", component.getVolumeName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"postgres@data", "data:/etc", "postgres data", "postgres\ndata", "_data", "${VOL}"})
    @DisplayName("볼륨 이름 규칙을 어긴 volumeName — INVALID_POSTGRES_VOLUME_NAME")
    void parse_InvalidVolumeName_ThrowsParsingException(String volumeName) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("volumeName", volumeName);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(
            ParsingErrorCode.INVALID_POSTGRES_VOLUME_NAME,
            exception.getCode()
        );
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
