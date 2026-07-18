package com.infragen.infragen.domain.parsing.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;

import tools.jackson.databind.ObjectMapper;

@DisplayName("Spring Boot 파서")
class SpringBootParserTest {

    private final SpringBootParser springBootParser = new SpringBootParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("containerName 누락 — MISSING_SPRING_BOOT_CONTAINER_NAME")
    void parse_MissingContainerName_ThrowsParsingException() {
        // given
        Map<String, Object> properties = validProperties();
        properties.remove("containerName");

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> springBootParser.parse(
                node(properties),
                objectMapper.valueToTree(properties),
                8080
            )
        );

        // then
        assertEquals(
            ParsingErrorCode.MISSING_SPRING_BOOT_CONTAINER_NAME,
            exception.getCode()
        );
    }

    private static NodeDTO node(Map<String, Object> properties) {
        return new NodeDTO(
            "node-1",
            "SPRING_BOOT",
            100f,
            200f,
            properties
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
