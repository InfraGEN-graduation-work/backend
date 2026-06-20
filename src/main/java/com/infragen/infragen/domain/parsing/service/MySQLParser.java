package com.infragen.infragen.domain.parsing.service;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLEnvComponent;
import com.infragen.infragen.domain.parsing.enums.ComponentType;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class MySQLParser implements ComponentParser{

    @Override
    public ComponentType getSupportedType() {
        return ComponentType.MYSQL;
    }

    @Override
    public void parseAndAddToResult(NodeDTO node, JsonNode props, int port, ParsingResultDTO result) {
        String imageVersion = props.path("imageVersion").asString();
        String containerName = props.path("containerName").asString();
        String volumeName = props.path("volumeName").asString();

        JsonNode envNode = props.path("env");
        String dbName = envNode.path("databaseName").asString();
        String dbPass = envNode.path("rootPassword").asString();
        String userPass = envNode.path("userPassword").asString();
        String username = envNode.path("username").asString();

        if (!dbName.matches("^[a-zA-Z0-9_]+$")) {
            throw new ParsingException(ParsingErrorCode.INVALID_DB_NAME);
        }
        if (dbPass == null || dbPass.length() < 8) {
            throw new ParsingException(ParsingErrorCode.INVALID_DB_PASSWORD);
        }

        MySQLEnvComponent env = MySQLEnvComponent.builder()
                .databaseName(dbName)
                .rootPassword(dbPass)
                .userPassword(userPass)
                .username(username)
                .build();

        MySQLComponent mySQL = MySQLComponent.builder()
                .id(node.getNodeId())
                .posX(node.getPositionX())
                .posY(node.getPositionY())
                .imageVersion(imageVersion)
                .containerName(containerName)
                .env(env)
                .port(port)
                .volumeName(volumeName)
                .build();

        result.getMySQL().add(mySQL);
    }

}
