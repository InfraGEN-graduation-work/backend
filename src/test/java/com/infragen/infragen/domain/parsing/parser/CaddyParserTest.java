package com.infragen.infragen.domain.parsing.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.CaddyComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;

import tools.jackson.databind.ObjectMapper;

@DisplayName("Caddy 파서")
class CaddyParserTest {

    private final CaddyParser caddyParser = new CaddyParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("포트를 받지 않는다 — 포트 범위 검사 대상이 아니다")
    void requiresPort_ReturnsFalse() {
        // when
        boolean requiresPort = caddyParser.requiresPort();

        // then
        assertFalse(requiresPort);
    }

    @Test
    @DisplayName("도메인과 볼륨이 있는 노드 — 핵심 속성 파싱 성공")
    void parse_ValidNodeWithDomain_ReturnsCaddyComponent() {
        // given
        Map<String, Object> properties = validProperties();

        // when
        CaddyComponent component = parse(properties);

        // then
        assertAll(
            () -> assertEquals("caddy-1", component.getNodeId()),
            () -> assertEquals("CADDY", component.getComponentType().name()),
            () -> assertEquals("caddy:2.8", component.getImageVersion()),
            () -> assertEquals("caddy", component.getContainerName()),
            () -> assertEquals("example.com", component.getDomain()),
            () -> assertEquals("caddy_data", component.getVolumeName())
        );
    }

    @Test
    @DisplayName("도메인이 없으면 볼륨 없이도 파싱 — HTTP만 서비스")
    void parse_WithoutDomain_AllowsMissingVolume() {
        // given
        Map<String, Object> properties = validProperties();
        properties.remove("domain");
        properties.remove("volumeName");

        // when
        CaddyComponent component = parse(properties);

        // then
        assertAll(
            () -> assertEquals("", component.getDomain()),
            () -> assertEquals("", component.getVolumeName())
        );
    }

    @Test
    @DisplayName("도메인 앞뒤 공백 — 제거해서 저장")
    void parse_DomainWithSurroundingSpaces_TrimsDomain() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("domain", "  example.com  ");

        // when
        CaddyComponent component = parse(properties);

        // then
        assertEquals("example.com", component.getDomain());
    }

    @Test
    @DisplayName("containerName 누락 — 빈 문자열로 파싱")
    void parse_MissingContainerName_ReturnsEmptyContainerName() {
        // given
        Map<String, Object> properties = validProperties();
        properties.remove("containerName");

        // when
        CaddyComponent component = parse(properties);

        // then
        assertEquals("", component.getContainerName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sub.example.com", "my-site.example.co.kr", "EXAMPLE.COM", "a.io"})
    @DisplayName("점을 포함한 호스트명 — 파싱 성공")
    void parse_ValidDomain_ReturnsComponent(String domain) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("domain", domain);

        // when
        CaddyComponent component = parse(properties);

        // then
        assertEquals(domain, component.getDomain());
    }

    @Test
    @DisplayName("이미지 버전 누락 — PARSING400_34")
    void parse_MissingImageVersion_Throws() {
        // given
        Map<String, Object> properties = validProperties();
        properties.remove("imageVersion");

        // when
        ParsingException exception = assertThrows(ParsingException.class, () -> parse(properties));

        // then
        assertEquals(ParsingErrorCode.MISSING_CADDY_IMAGE_VERSION, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "localhost", "10.0.0.1", "exa mple.com", "example..com", "-a.example.com", "example.com-",
        "example.com/path", "{evil}.com", "example.com {", "example.com\nrespond", "*.example.com", "https://example.com",
        "한글.com"
    })
    @DisplayName("호스트명 형식이 아닌 도메인 — PARSING400_35")
    void parse_InvalidDomain_Throws(String domain) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("domain", domain);

        // when
        ParsingException exception = assertThrows(ParsingException.class, () -> parse(properties));

        // then
        assertEquals(ParsingErrorCode.INVALID_CADDY_DOMAIN, exception.getCode());
    }

    @Test
    @DisplayName("도메인이 있는데 볼륨 이름 누락 — PARSING400_36")
    void parse_DomainWithoutVolume_Throws() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("volumeName", "  ");

        // when
        ParsingException exception = assertThrows(ParsingException.class, () -> parse(properties));

        // then
        assertEquals(ParsingErrorCode.MISSING_CADDY_VOLUME_NAME, exception.getCode());
    }

    private CaddyComponent parse(Map<String, Object> properties) {
        NodeDTO node = new NodeDTO("caddy-1", "CADDY", 100f, 200f, properties);

        return (CaddyComponent) caddyParser.parse(node, objectMapper.valueToTree(properties), 0);
    }

    private static Map<String, Object> validProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("imageVersion", "caddy:2.8");
        properties.put("containerName", "caddy");
        properties.put("domain", "example.com");
        properties.put("volumeName", "caddy_data");
        return properties;
    }
}
