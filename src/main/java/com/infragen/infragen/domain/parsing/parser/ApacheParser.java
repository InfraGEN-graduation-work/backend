package com.infragen.infragen.domain.parsing.parser;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.ApacheComponent;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;

@Component
public class ApacheParser implements ComponentParser {

    @Override
    public ComponentType getSupportedType() {
        return ComponentType.APACHE;
    }

    @Override
    public BaseComponent parse(NodeDTO node, JsonNode props, int port) {
        String imageVersion = props.path("imageVersion").asString();
        String containerName = props.path("containerName").asString();
        String configVolumeName = props.path("configVolumeName").asString();
        String documentRootVolumeName = props.path("documentRootVolumeName").asString();

        if (imageVersion == null || imageVersion.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_APACHE_IMAGE_VERSION);
        }

        float posX = node.getPositionX() != null ? node.getPositionX() : 0f;
        float posY = node.getPositionY() != null ? node.getPositionY() : 0f;

        return ApacheComponent.builder()
                .id(node.getNodeId())
                .posX(posX)
                .posY(posY)
                .imageVersion(imageVersion)
                .containerName(containerName != null ? containerName : "")
                .port(port)
                .configVolumeName(configVolumeName != null ? configVolumeName : "")
                .documentRootVolumeName(documentRootVolumeName != null ? documentRootVolumeName : "")
                .build();
    }
}
