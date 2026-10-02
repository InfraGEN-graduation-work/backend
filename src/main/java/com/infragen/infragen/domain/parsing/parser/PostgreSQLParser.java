package com.infragen.infragen.domain.parsing.parser;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLEnvComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;

/**
 * POSTGRESQL 노드 속성을 검증하고 {@link PostgreSQLComponent}로 변환한다.
 *
 * <p>포트 범위와 중복은 {@code ParsingService}가 검사한 뒤 {@code port}로 전달한다.
 * MySQL과 달리 root 비밀번호를 받지 않는다. 공식 이미지에서 {@code POSTGRES_USER}가 superuser이기 때문이다.
 */
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
        String dbName = envNode.path("databaseName").asString();
        String password = envNode.path("password").asString();
        String username = envNode.path("username").asString();

        if (dbName == null || dbName.isBlank() || !dbName.matches("^[a-zA-Z0-9_]+$")) {
            throw new ParsingException(ParsingErrorCode.INVALID_DB_NAME);
        }
        if (password == null || password.length() < 8) {
            throw new ParsingException(ParsingErrorCode.INVALID_DB_PASSWORD);
        }
        if (imageVersion == null || imageVersion.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_POSTGRES_IMAGE_VERSION);
        }
        if (username == null || username.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_POSTGRES_USERNAME);
        }

        float posX = node.getPositionX() != null ? node.getPositionX() : 0f;
        float posY = node.getPositionY() != null ? node.getPositionY() : 0f;

        PostgreSQLEnvComponent env = PostgreSQLEnvComponent.builder()
            .databaseName(dbName)
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
