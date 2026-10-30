package com.infragen.infragen.domain.parsing.parser;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.RedisComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.domain.parsing.validator.ComponentPropertyField;
import com.infragen.infragen.domain.parsing.validator.ComposeYamlValueValidator;
import com.infragen.infragen.domain.parsing.validator.EnvValueValidator;
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
        String imageVersion = ComponentPropertyField.REDIS_IMAGE_VERSION.read(props);
        String containerName = ComponentPropertyField.REDIS_CONTAINER_NAME.read(props);
        String volumeName = ComponentPropertyField.REDIS_VOLUME_NAME.read(props);
        String password = ComponentPropertyField.REDIS_PASSWORD.read(props);

        if (imageVersion == null || imageVersion.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_REDIS_IMAGE_VERSION);
        }
        String resolvedImageVersion = imageVersion.trim();
        if (!ComposeYamlValueValidator.isImageReference(resolvedImageVersion)) {
            throw new ParsingException(ParsingErrorCode.INVALID_REDIS_IMAGE_VERSION);
        }
        String resolvedVolumeName = volumeName == null ? "" : volumeName.trim();
        // 볼륨은 선택 항목이라 이름이 있을 때만 형식을 검사한다.
        if (!resolvedVolumeName.isEmpty() && !ComposeYamlValueValidator.isVolumeName(resolvedVolumeName)) {
            throw new ParsingException(ParsingErrorCode.INVALID_REDIS_VOLUME_NAME);
        }
        String resolvedContainerName = containerName == null ? "" : containerName.trim();
        // 비면 renderer가 service 이름으로 대체하므로 이름이 있을 때만 형식을 검사한다.
        if (!resolvedContainerName.isEmpty() && !ComposeYamlValueValidator.isContainerName(resolvedContainerName)) {
            throw new ParsingException(ParsingErrorCode.INVALID_REDIS_CONTAINER_NAME);
        }
        if (password == null || password.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_REDIS_PASSWORD);
        }
        EnvValueValidator.validate(password);

        float posX = node.getPositionX() != null ? node.getPositionX() : 0f;
        float posY = node.getPositionY() != null ? node.getPositionY() : 0f;

        return RedisComponent.builder()
            .id(node.getNodeId())
            .posX(posX)
            .posY(posY)
            .imageVersion(resolvedImageVersion)
            .containerName(resolvedContainerName)
            .port(port)
            .volumeName(resolvedVolumeName)
            .password(password != null ? password : "")
            .build();
    }
}
