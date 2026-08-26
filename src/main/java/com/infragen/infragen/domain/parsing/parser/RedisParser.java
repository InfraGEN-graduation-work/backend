package com.infragen.infragen.domain.parsing.parser;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.RedisComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;

@Component
public class RedisParser implements ComponentParser {

    @Override
    public ComponentType getSupportedType() {
        return ComponentType.REDIS;
    }

    @Override
    public BaseComponent parse(NodeDTO node, JsonNode props, int port) {
        String imageVersion = props.path("imageVersion").asString();
        String containerName = props.path("containerName").asString();
        String volumeName = props.path("volumeName").asString();
        String password = props.path("password").asString();

        if (imageVersion == null || imageVersion.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_REDIS_IMAGE_VERSION);
        }
        if (password == null || password.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_REDIS_PASSWORD);
        }

        float posX = node.getPositionX() != null ? node.getPositionX() : 0f;
        float posY = node.getPositionY() != null ? node.getPositionY() : 0f;

        return RedisComponent.builder()
            .id(node.getNodeId())
            .posX(posX)
            .posY(posY)
            .imageVersion(imageVersion)
            .containerName(containerName != null ? containerName : "")
            .port(port)
            .volumeName(volumeName != null ? volumeName : "")
            .password(password != null ? password : "")
            .build();
    }
}
