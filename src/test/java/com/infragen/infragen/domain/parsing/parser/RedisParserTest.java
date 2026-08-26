package com.infragen.infragen.domain.parsing.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.RedisComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;

import tools.jackson.databind.ObjectMapper;

@DisplayName("Redis 파서")
class RedisParserTest {

    private final RedisParser redisParser = new RedisParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("유효한 Redis 노드 — 핵심 속성 파싱 성공")
    void parse_ValidNode_ReturnsRedisComponent() {
        // given
        Map<String, Object> properties = validProperties();

        // when
        RedisComponent component = (RedisComponent) parse(properties);

        // then
        assertAll(
            () -> assertEquals("node-1", component.getNodeId()),
            () -> assertEquals("REDIS", component.getComponentType().name()),
            () -> assertEquals("redis:7.4", component.getImageVersion()),
            () -> assertEquals("redis", component.getContainerName()),
            () -> assertEquals("redis_data", component.getVolumeName()),
            () -> assertEquals(6379, component.getPort())
        );
    }

    @Test
    @DisplayName("imageVersion 누락 — MISSING_REDIS_IMAGE_VERSION")
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
        assertEquals(ParsingErrorCode.MISSING_REDIS_IMAGE_VERSION, exception.getCode());
    }

    private RedisComponent parse(Map<String, Object> properties) {
        NodeDTO node = new NodeDTO(
            "node-1",
            "REDIS",
            100f,
            200f,
            properties
        );

        return (RedisComponent) redisParser.parse(
            node,
            objectMapper.valueToTree(properties),
            6379
        );
    }

    private static Map<String, Object> validProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("imageVersion", "redis:7.4");
        properties.put("containerName", "redis");
        properties.put("volumeName", "redis_data");
        return properties;
    }
}
