package com.infragen.infragen.domain.parsing.parser;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLEnvComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;

@Component
public class MySQLParser implements ComponentParser {

    @Override
    public ComponentType getSupportedType() {
        return ComponentType.MYSQL;
    }

    @Override
    public BaseComponent parse(NodeDTO node, JsonNode props, int port) {
        String imageVersion = props.path("imageVersion").asString();
        String containerName = props.path("containerName").asString();
        String volumeName = props.path("volumeName").asString();

        JsonNode envNode = props.path("env");
        String dbName = envNode.path("databaseName").asString();
        String dbPass = envNode.path("rootPassword").asString();
        String userPass = envNode.path("userPassword").asString();
        String username = envNode.path("username").asString();

        if (dbName == null || dbName.isBlank() || !dbName.matches("^[a-zA-Z0-9_]+$")) {
            throw new ParsingException(ParsingErrorCode.INVALID_DB_NAME);
        }
        if (dbPass == null || dbPass.length() < 8) {
            throw new ParsingException(ParsingErrorCode.INVALID_DB_PASSWORD);
        }
        if (imageVersion == null || imageVersion.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_MYSQL_IMAGE_VERSION);
        }
        if (containerName == null || containerName.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_MYSQL_CONTAINER_NAME);
        }
        if (volumeName == null || volumeName.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_MYSQL_VOLUME_NAME);
        }
        if (username == null || username.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_MYSQL_USERNAME);
        }
        if (userPass == null || userPass.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_MYSQL_USER_PASSWORD);
        }

        float posX = node.getPositionX() != null ? node.getPositionX() : 0f;
        float posY = node.getPositionY() != null ? node.getPositionY() : 0f;

        MySQLEnvComponent env = MySQLEnvComponent.builder()
            .databaseName(dbName)
            .rootPassword(dbPass)
            .userPassword(userPass)
            .username(username)
            .build();

        return MySQLComponent.builder()
            .id(node.getNodeId())
            .posX(posX)
            .posY(posY)
            .imageVersion(imageVersion)
            .containerName(containerName)
            .env(env)
            .port(port)
            .volumeName(volumeName)
            .build();
    }
}
