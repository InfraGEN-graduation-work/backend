package com.infragen.infragen.domain.parsing.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.NginxComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import tools.jackson.databind.ObjectMapper;

class NginxParserTest {
    private final NginxParser parser = new NginxParser();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parse_MissingContainerName_UsesNginxDefault() {
        // given
        Map<String, Object> properties = Map.of("imageVersion", "nginx:stable");
        NodeDTO node = new NodeDTO("proxy", "NGINX", null, null, properties);
        // when
        NginxComponent result = (NginxComponent) parser.parse(node, mapper.valueToTree(properties), 80);
        // then
        assertAll(() -> assertEquals("nginx", result.getContainerName()),
            () -> assertEquals(80, result.getPort()));
    }

    @ParameterizedTest
    @MethodSource("invalidProperties")
    void parse_InvalidProperties_RejectsInput(Map<String, Object> properties) {
        // given
        NodeDTO node = new NodeDTO("proxy", "NGINX", 0f, 0f, properties);
        // when
        ParsingException exception = assertThrows(ParsingException.class,
            () -> parser.parse(node, mapper.valueToTree(properties), 80));
        // then
        assertEquals(ParsingErrorCode.INVALID_NGINX_PROPERTIES, exception.getCode());
    }

    static Stream<Map<String, Object>> invalidProperties() {
        return Stream.of(
            Map.of(), Map.of("imageVersion", " "), Map.of("imageVersion", 123),
            Map.of("imageVersion", "nginx:stable\n    privileged: true"),
            Map.of("imageVersion", "${IMAGE}"),
            Map.of("imageVersion", "nginx:stable", "containerName", "bad name"),
            Map.of("imageVersion", "nginx:stable", "containerName", 123),
            Map.of("imageVersion", "nginx:stable", "routes", java.util.List.of())
        );
    }
}
