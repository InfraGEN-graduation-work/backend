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
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;

import tools.jackson.databind.ObjectMapper;

@DisplayName("MySQL 파서")
class MySQLParserTest {

    private final MySQLParser mySQLParser = new MySQLParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("유효한 MySQL 노드 — 핵심 속성 파싱 성공")
    void parse_ValidNode_ReturnsMySQLComponent() {
        // given
        Map<String, Object> properties = validProperties();

        // when
        MySQLComponent component = (MySQLComponent) parse(properties);

        // then
        assertAll(
            () -> assertEquals("node-1", component.getNodeId()),
            () -> assertEquals("MYSQL", component.getComponentType().name()),
            () -> assertEquals("mysql:8.0", component.getImageVersion()),
            () -> assertEquals("mysql", component.getContainerName()),
            () -> assertEquals("mysql_data", component.getVolumeName())
        );
    }

    @Test
    @DisplayName("imageVersion 누락 — MISSING_MYSQL_IMAGE_VERSION")
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
        assertEquals(
            ParsingErrorCode.MISSING_MYSQL_IMAGE_VERSION,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("imageVersion 빈 값 — MISSING_MYSQL_IMAGE_VERSION")
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
        assertEquals(
            ParsingErrorCode.MISSING_MYSQL_IMAGE_VERSION,
            exception.getCode()
        );
    }

    @Test
    @DisplayName("username 누락 — MISSING_MYSQL_USERNAME")
    void parse_MissingUsername_ThrowsParsingException() {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).remove("username");

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(
            ParsingErrorCode.MISSING_MYSQL_USERNAME,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("username 빈 값 — MISSING_MYSQL_USERNAME")
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
        assertEquals(
            ParsingErrorCode.MISSING_MYSQL_USERNAME,
            exception.getCode()
        );
    }

    @Test
    @DisplayName("userPassword 누락 — MISSING_MYSQL_USER_PASSWORD")
    void parse_MissingUserPassword_ThrowsParsingException() {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).remove("userPassword");

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(
            ParsingErrorCode.MISSING_MYSQL_USER_PASSWORD,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("userPassword 빈 값 — MISSING_MYSQL_USER_PASSWORD")
    void parse_BlankUserPassword_ThrowsParsingException(String userPassword) {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).put("userPassword", userPassword);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(
            ParsingErrorCode.MISSING_MYSQL_USER_PASSWORD,
            exception.getCode()
        );
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
    @DisplayName("rootPassword 금지 문자 — UNSUPPORTED_ENV_VALUE_CHARACTER")
    void parse_RootPasswordWithForbiddenCharacter_ThrowsParsingException(String value) {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).put("rootPassword", value);

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
    @ValueSource(strings = {"a\"b", "a\\b", "a$b", "a\nb", "a\rb", "a\0b"})
    @DisplayName("userPassword 금지 문자 — UNSUPPORTED_ENV_VALUE_CHARACTER")
    void parse_UserPasswordWithForbiddenCharacter_ThrowsParsingException(String value) {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).put("userPassword", value);

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

    @Test
    @DisplayName("rootPassword 길이 부족과 금지 문자 겹침 — INVALID_DB_PASSWORD 우선")
    void parse_ShortRootPasswordWithForbiddenCharacter_ThrowsInvalidDbPassword() {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).put("rootPassword", "a$b");

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.INVALID_DB_PASSWORD, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "mysql", "mysql:8.0-alpine", "ghcr.io/org/mysql:8.0", "localhost:5000/mysql:8.0",
        "mysql@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    })
    @DisplayName("Docker 이미지 참조 형식 — 파싱 성공")
    void parse_ValidImageVersion_ReturnsComponent(String imageVersion) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("imageVersion", imageVersion);

        // when
        MySQLComponent component = (MySQLComponent) parse(properties);

        // then
        assertEquals(imageVersion, component.getImageVersion());
    }

    @Test
    @DisplayName("imageVersion 앞뒤 공백 — 제거해서 저장")
    void parse_ImageVersionWithSurroundingSpaces_TrimsImageVersion() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("imageVersion", " mysql:8.0 ");

        // when
        MySQLComponent component = (MySQLComponent) parse(properties);

        // then
        assertEquals("mysql:8.0", component.getImageVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "mysql:8.0\n    privileged: true", "mysql 8.0", "${IMAGE}", "mysql:",
        "mysql::8.0", "Mysql:8.0", "mysql@sha256:abc123"
    })
    @DisplayName("YAML 구조를 바꾸거나 이미지 참조 구조가 아닌 imageVersion — INVALID_MYSQL_IMAGE_VERSION")
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
            ParsingErrorCode.INVALID_MYSQL_IMAGE_VERSION,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql_data", "mysql-data.1", "Data1"})
    @DisplayName("Docker 볼륨 이름 규칙 — 파싱 성공")
    void parse_ValidVolumeName_ReturnsComponent(String volumeName) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("volumeName", volumeName);

        // when
        MySQLComponent component = (MySQLComponent) parse(properties);

        // then
        assertEquals(volumeName, component.getVolumeName());
    }

    @Test
    @DisplayName("volumeName 앞뒤 공백 — 제거해서 저장")
    void parse_VolumeNameWithSurroundingSpaces_TrimsVolumeName() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("volumeName", "  mysql_data  ");

        // when
        MySQLComponent component = (MySQLComponent) parse(properties);

        // then
        assertEquals("mysql_data", component.getVolumeName());
    }

    @Test
    @DisplayName("volumeName 공백뿐인 값 — 볼륨 없이 파싱")
    void parse_BlankVolumeName_ReturnsEmptyVolumeName() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("volumeName", "  ");

        // when
        MySQLComponent component = (MySQLComponent) parse(properties);

        // then
        assertEquals("", component.getVolumeName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql@data", "data:/etc", "mysql data", "mysql\ndata", "_data", "${VOL}"})
    @DisplayName("볼륨 이름 규칙을 어긴 volumeName — INVALID_MYSQL_VOLUME_NAME")
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
            ParsingErrorCode.INVALID_MYSQL_VOLUME_NAME,
            exception.getCode()
        );
    }

    private BaseComponent parse(Map<String, Object> properties) {
        NodeDTO node = new NodeDTO(
            "node-1",
            "MYSQL",
            100f,
            200f,
            properties
        );

        return mySQLParser.parse(
            node,
            objectMapper.valueToTree(properties),
            3306
        );
    }

    private static Map<String, Object> validProperties() {
        Map<String, Object> env = new HashMap<>();
        env.put("databaseName", "appdb");
        env.put("username", "user");
        env.put("userPassword", "userpass12");
        env.put("rootPassword", "rootpass12");

        Map<String, Object> properties = new HashMap<>();
        properties.put("imageVersion", "mysql:8.0");
        properties.put("containerName", "mysql");
        properties.put("volumeName", "mysql_data");
        properties.put("port", 3306);
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
