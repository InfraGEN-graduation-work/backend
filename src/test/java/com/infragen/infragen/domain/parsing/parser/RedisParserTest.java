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
            () -> assertEquals("redis-password", component.getPassword()),
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

    @Test
    @DisplayName("password 누락 — MISSING_REDIS_PASSWORD")
    void parse_MissingPassword_ThrowsParsingException() {
        // given
        Map<String, Object> properties = validProperties();
        properties.remove("password");

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.MISSING_REDIS_PASSWORD, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\"b", "a\\b", "a$b", "a\nb", "a\rb", "a\0b"})
    @DisplayName("password 금지 문자 — UNSUPPORTED_ENV_VALUE_CHARACTER")
    void parse_PasswordWithForbiddenCharacter_ThrowsParsingException(String value) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("password", value);

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
        "redis", "redis:7.4-alpine", "ghcr.io/org/redis:7.4", "localhost:5000/redis:7.4",
        "redis@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    })
    @DisplayName("Docker 이미지 참조 형식 — 파싱 성공")
    void parse_ValidImageVersion_ReturnsComponent(String imageVersion) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("imageVersion", imageVersion);

        // when
        RedisComponent component = parse(properties);

        // then
        assertEquals(imageVersion, component.getImageVersion());
    }

    @Test
    @DisplayName("imageVersion 앞뒤 공백 — 제거해서 저장")
    void parse_ImageVersionWithSurroundingSpaces_TrimsImageVersion() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("imageVersion", " redis:7.4 ");

        // when
        RedisComponent component = parse(properties);

        // then
        assertEquals("redis:7.4", component.getImageVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "redis:7.4\n    privileged: true", "redis 7.4", "${IMAGE}", "redis:",
        "redis::7.4", "Redis:7.4", "redis@sha256:abc123"
    })
    @DisplayName("YAML 구조를 바꾸거나 이미지 참조 구조가 아닌 imageVersion — INVALID_REDIS_IMAGE_VERSION")
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
            ParsingErrorCode.INVALID_REDIS_IMAGE_VERSION,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"redis_data", "redis-data.1", "Data1"})
    @DisplayName("Docker 볼륨 이름 규칙 — 파싱 성공")
    void parse_ValidVolumeName_ReturnsComponent(String volumeName) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("volumeName", volumeName);

        // when
        RedisComponent component = parse(properties);

        // then
        assertEquals(volumeName, component.getVolumeName());
    }

    @Test
    @DisplayName("volumeName 앞뒤 공백 — 제거해서 저장")
    void parse_VolumeNameWithSurroundingSpaces_TrimsVolumeName() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("volumeName", "  redis_data  ");

        // when
        RedisComponent component = parse(properties);

        // then
        assertEquals("redis_data", component.getVolumeName());
    }

    @Test
    @DisplayName("volumeName 공백뿐인 값 — 볼륨 없이 파싱")
    void parse_BlankVolumeName_ReturnsEmptyVolumeName() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("volumeName", "  ");

        // when
        RedisComponent component = parse(properties);

        // then
        assertEquals("", component.getVolumeName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"redis@data", "data:/etc", "redis data", "redis\ndata", "_data", "${VOL}"})
    @DisplayName("볼륨 이름 규칙을 어긴 volumeName — INVALID_REDIS_VOLUME_NAME")
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
            ParsingErrorCode.INVALID_REDIS_VOLUME_NAME,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"redis-db", "redis_db.1", "DB1"})
    @DisplayName("Docker 컨테이너 이름 규칙 — 파싱 성공")
    void parse_ValidContainerName_ReturnsComponent(String containerName) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("containerName", containerName);

        // when
        RedisComponent component = parse(properties);

        // then
        assertEquals(containerName, component.getContainerName());
    }

    @Test
    @DisplayName("containerName 앞뒤 공백 — 제거해서 저장")
    void parse_ContainerNameWithSurroundingSpaces_TrimsContainerName() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("containerName", "  redis-db  ");

        // when
        RedisComponent component = parse(properties);

        // then
        assertEquals("redis-db", component.getContainerName());
    }

    @Test
    @DisplayName("containerName 공백뿐인 값 — 빈 값으로 파싱")
    void parse_BlankContainerName_ReturnsEmptyContainerName() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("containerName", "  ");

        // when
        RedisComponent component = parse(properties);

        // then
        assertEquals("", component.getContainerName());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "redis\n    privileged: true", "redis db", "redis:1", "redis/1", "${NAME}", "_redis", "a"
    })
    @DisplayName("컨테이너 이름 규칙을 어긴 containerName — INVALID_REDIS_CONTAINER_NAME")
    void parse_InvalidContainerName_ThrowsParsingException(String containerName) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("containerName", containerName);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(
            ParsingErrorCode.INVALID_REDIS_CONTAINER_NAME,
            exception.getCode()
        );
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
        properties.put("password", "redis-password");
        return properties;
    }
}
