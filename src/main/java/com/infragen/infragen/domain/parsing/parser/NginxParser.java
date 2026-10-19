package com.infragen.infragen.domain.parsing.parser;

import org.springframework.stereotype.Component;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.NginxComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;
import tools.jackson.databind.JsonNode;

@Component
public class NginxParser implements ComponentParser {
    @Override
    public ComponentType getSupportedType() {
        return ComponentType.NGINX;
    }

    @Override
    public BaseComponent parse(NodeDTO node, JsonNode props, int port) {
        JsonNode image = props.path("imageVersion");
        if (!image.isString() || image.asString().isBlank()) {
            throw new ParsingException(ParsingErrorCode.INVALID_NGINX_PROPERTIES);
        }
        String imageVersion = image.asString().trim();
        JsonNode name = props.path("containerName");
        if (!name.isMissingNode() && !name.isNull() && !name.isString()) {
            throw new ParsingException(ParsingErrorCode.INVALID_NGINX_PROPERTIES);
        }
        String containerName = name.isMissingNode() || name.isNull() ? "nginx" : name.asString();
        if (containerName != null && containerName.isBlank()) {
            containerName = "nginx";
        }
        // YAML 구문과 Compose 변수 삽입을 차단한다. 경로 라우팅 등은 이번 계약에 없다.
        if (!imageVersion.matches("[a-zA-Z0-9][a-zA-Z0-9._/:@-]*")
            || containerName == null || !containerName.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]*")
            || props.has("routes") || props.has("path") || props.has("upstream")) {
            throw new ParsingException(ParsingErrorCode.INVALID_NGINX_PROPERTIES);
        }
        return NginxComponent.builder()
            .id(node.getNodeId())
            .posX(node.getPositionX() == null ? 0f : node.getPositionX())
            .posY(node.getPositionY() == null ? 0f : node.getPositionY())
            .imageVersion(imageVersion)
            .containerName(containerName)
            .port(port)
            .build();
    }
}
