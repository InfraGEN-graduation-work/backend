package com.infragen.infragen.domain.parsing.parser;
import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.env.PostgreSQLEnvComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;

@Component
public class PostgreSQLParser implements ComponentParser {

    @Override
    public ComponentType getSupportedType() {
        return ComponentType.POSTGRESQL;
    }

    @Override
    public BaseComponent parse(NodeDTO node, JsonNode props, int port) {
        String imageVersion = props.path("imageVersion").asString();
        String containerName = props.path("containerName").asString();
        String volumeName = props.path("volumeName").asString();

        JsonNode envNode = props.path("env");
        String databaseName = envNode.path("databaseName").asString();
        String username = envNode.path("username").asString();
        String password = envNode.path("password").asString();

        if (databaseName == null || databaseName.isBlank() || !databaseName.matches("^[a-zA-Z0-9_]+$")) {
            throw new ParsingException(ParsingErrorCode.INVALID_DB_NAME);
        }
        if (password == null || password.length() < 8) {
            throw new ParsingException(ParsingErrorCode.INVALID_DB_PASSWORD);
        }
        if (imageVersion == null || imageVersion.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_POSTGRESQL_IMAGE_VERSION);
        }
        if (username == null || username.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_POSTGRESQL_USERNAME);
        }

        float posX = node.getPositionX() != null ? node.getPositionX() : 0f;

        float posY = node.getPositionY() != null ? node.getPositionY() : 0f;

        PostgreSQLEnvComponent env = PostgreSQLEnvComponent.builder()
                .databaseName(databaseName)
                .username(username)
                .password(password)
                .build();

        return PostgreSQLComponent.builder()
                .id(node.getNodeId())
                .posX(posX)
                .posY(posY)
                .imageVersion(imageVersion)
                .containerName(containerName != null ? containerName : "")
                .env(env)
                .port(port)
                .volumeName(volumeName != null ? volumeName : "")
                .build();
    }
}