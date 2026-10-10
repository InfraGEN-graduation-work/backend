package com.infragen.infragen.domain.parsing.parser;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.NodeJsComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;

/**
 * NODE_JS 애플리케이션 노드의 속성을 검증하고 {@link NodeJsComponent}로 바꾼다.
 *
 * <p>포트 범위와 그래프 내 중복은 {@code ParsingService}가 검사한다. 실행 명령은 속성으로 받지 않고
 * {@code npm start}로 고정한다. {@code nodeVersion}은 Dockerfile의 {@code FROM} 줄에 들어가므로 숫자만 허용한다.
 */
@Component
public class NodeJsParser implements ComponentParser {

    @Override
    public ComponentType getSupportedType() {
        return ComponentType.NODE_JS;
    }

    @Override
    public BaseComponent parse(NodeDTO node, JsonNode props, int port) {
        String name = props.path("name").asString();
        String nodeVersion = props.path("nodeVersion").asString();
        String containerName = props.path("containerName").asString();

        if (name == null || name.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_NODE_JS_NAME);
        }
        if (nodeVersion == null || nodeVersion.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_NODE_VERSION);
        }
        if (!nodeVersion.matches("[0-9]+")) {
            throw new ParsingException(ParsingErrorCode.INVALID_NODE_VERSION);
        }

        float posX = node.getPositionX() != null ? node.getPositionX() : 0f;
        float posY = node.getPositionY() != null ? node.getPositionY() : 0f;
        String resolvedContainerName = containerName != null ? containerName : "";

        return NodeJsComponent.builder()
            .id(node.getNodeId())
            .posX(posX)
            .posY(posY)
            .name(name)
            .port(port)
            .nodeVersion(nodeVersion)
            .containerName(resolvedContainerName)
            .build();
    }
}
