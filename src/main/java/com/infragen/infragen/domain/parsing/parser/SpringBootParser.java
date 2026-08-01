package com.infragen.infragen.domain.parsing.parser;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;

@Component
public class SpringBootParser implements ComponentParser {
    @Override
    public ComponentType getSupportedType() { return ComponentType.SPRING_BOOT; }

    @Override
    public BaseComponent parse(NodeDTO node, JsonNode props, int port) {
        String name = props.path("name").asString();
        String javaVersion = props.path("javaVersion").asString();
        String containerName = props.path("containerName").asString();

        if (name == null || name.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_SPRING_BOOT_NAME);
        }
        if (javaVersion == null || javaVersion.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_JAVA_VERSION);
        }

        float posX = node.getPositionX() != null ? node.getPositionX() : 0f;
        float posY = node.getPositionY() != null ? node.getPositionY() : 0f;
        String resolvedContainerName = containerName != null ? containerName : "";

        return SpringBootComponent.builder()
            .id(node.getNodeId())
            .posX(posX)
            .posY(posY)
            .name(name)
            .port(port)
            .javaVersion(javaVersion)
            .containerName(resolvedContainerName)
            .build();
    }
}
