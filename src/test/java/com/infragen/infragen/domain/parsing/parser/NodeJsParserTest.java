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
import com.infragen.infragen.domain.parsing.dto.response.NodeJsComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;

import tools.jackson.databind.ObjectMapper;

@DisplayName("Node.js 파서")
class NodeJsParserTest {

    private final NodeJsParser nodeJsParser = new NodeJsParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("유효한 Node.js 노드 — 핵심 속성 파싱 성공")
    void parse_ValidNode_ReturnsNodeJsComponent() {
        // given
        Map<String, Object> properties = validProperties();

        // when
        NodeJsComponent component = parse(properties);

        // then
        assertAll(
            () -> assertEquals("node-1", component.getNodeId()),
            () -> assertEquals("NODE_JS", component.getComponentType().name()),
            () -> assertEquals("api", component.getName()),
            () -> assertEquals(3000, component.getPort()),
            () -> assertEquals("22", component.getNodeVersion()),
            () -> assertEquals("node-app", component.getContainerName())
        );
    }

    @Test
    @DisplayName("containerName 누락 — 빈 문자열로 파싱")
    void parse_MissingContainerName_ReturnsEmptyContainerName() {
        // given
        Map<String, Object> properties = validProperties();
        properties.remove("containerName");

        // when
        NodeJsComponent component = parse(properties);

        // then
        assertEquals("", component.getContainerName());
    }

    @Test
    @DisplayName("name 누락 — MISSING_NODE_JS_NAME")
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
        assertEquals(ParsingErrorCode.MISSING_NODE_JS_NAME, exception.getCode());
    }

    @Test
    @DisplayName("nodeVersion 누락 — MISSING_NODE_VERSION")
    void parse_MissingNodeVersion_ThrowsParsingException() {
        // given
        Map<String, Object> properties = validProperties();
        properties.remove("nodeVersion");

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.MISSING_NODE_VERSION, exception.getCode());
    }

    @ParameterizedTest(name = "nodeVersion=[{0}]")
    @ValueSource(strings = {"22.1", "v22", "22 && rm -rf /", "lts"})
    @DisplayName("nodeVersion이 숫자가 아니면 INVALID_NODE_VERSION")
    void parse_NonNumericNodeVersion_ThrowsParsingException(String nodeVersion) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("nodeVersion", nodeVersion);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.INVALID_NODE_VERSION, exception.getCode());
    }

    private NodeJsComponent parse(Map<String, Object> properties) {
        NodeDTO node = new NodeDTO(
            "node-1",
            "NODE_JS",
            100f,
            200f,
            properties
        );

        return (NodeJsComponent) nodeJsParser.parse(
            node,
            objectMapper.valueToTree(properties),
            3000
        );
    }

    private static Map<String, Object> validProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("name", "api");
        properties.put("nodeVersion", "22");
        properties.put("containerName", "node-app");
        return properties;
    }
}
