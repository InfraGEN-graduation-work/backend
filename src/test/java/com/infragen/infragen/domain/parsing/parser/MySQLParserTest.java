package com.infragen.infragen.domain.parsing.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
