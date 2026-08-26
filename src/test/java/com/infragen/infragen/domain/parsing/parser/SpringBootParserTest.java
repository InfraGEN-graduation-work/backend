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
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;

import tools.jackson.databind.ObjectMapper;

@DisplayName("Spring Boot 파서")
class SpringBootParserTest {

    private final SpringBootParser springBootParser = new SpringBootParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("유효한 Spring Boot 노드 — 핵심 속성 파싱 성공")
    void parse_ValidNode_ReturnsSpringBootComponent() {
        // given
        Map<String, Object> properties = validProperties();

        // when
        SpringBootComponent component = parse(properties);

        // then
        assertAll(
            () -> assertEquals("node-1", component.getNodeId()),
            () -> assertEquals("SPRING_BOOT", component.getComponentType().name()),
            () -> assertEquals("app", component.getName()),
            () -> assertEquals(8080, component.getPort()),
            () -> assertEquals("17", component.getJavaVersion()),
            () -> assertEquals("spring-app", component.getContainerName())
        );
    }

    @Test
    @DisplayName("name 누락 — MISSING_SPRING_BOOT_NAME")
    void parse_MissingName_ThrowsParsingException() {
        // given
        Map<String, Object> properties = validProperties();
        properties.remove("name");

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(
            ParsingErrorCode.MISSING_SPRING_BOOT_NAME,
            exception.getCode()
        );
    }

    @Test
    @DisplayName("javaVersion 누락 — MISSING_JAVA_VERSION")
    void parse_MissingJavaVersion_ThrowsParsingException() {
        // given
        Map<String, Object> properties = validProperties();
        properties.remove("javaVersion");

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(
            ParsingErrorCode.MISSING_JAVA_VERSION,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"17.0", "17-beta"})
    @DisplayName("Java version 형식 오류 — INVALID_JAVA_VERSION")
    void parse_InvalidJavaVersion_ThrowsParsingException(String javaVersion) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("javaVersion", javaVersion);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.INVALID_JAVA_VERSION, exception.getCode());
    }

    private SpringBootComponent parse(Map<String, Object> properties) {
        NodeDTO node = new NodeDTO(
            "node-1",
            "SPRING_BOOT",
            100f,
            200f,
            properties
        );

        return (SpringBootComponent) springBootParser.parse(
            node,
            objectMapper.valueToTree(properties),
            8080
        );
    }

    private static Map<String, Object> validProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("name", "app");
        properties.put("port", 8080);
        properties.put("javaVersion", "17");
        properties.put("containerName", "spring-app");
        return properties;
    }
}
