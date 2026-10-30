package com.infragen.infragen.domain.parsing.validator;

import java.util.Arrays;
import java.util.List;

import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;

/**
 * 노드 {@code properties}에서 형식 검증 대상인 속성의 위치, 분류, 위반 시 에러 코드를 정의한다.
 *
 * <p>컴포넌트 파서와 {@link ComponentPropertyValidator}가 같은 위치에서 속성 값을 읽고 일관된 검증을 수행하도록 보장한다.
 */
public enum ComponentPropertyField {

    // MySQL
    MYSQL_IMAGE_VERSION(ComponentType.MYSQL, PropertyFieldKind.IMAGE_VERSION, ParsingErrorCode.INVALID_MYSQL_IMAGE_VERSION, "imageVersion"),
    MYSQL_CONTAINER_NAME(ComponentType.MYSQL, PropertyFieldKind.CONTAINER_NAME, ParsingErrorCode.INVALID_MYSQL_CONTAINER_NAME, "containerName"),
    MYSQL_VOLUME_NAME(ComponentType.MYSQL, PropertyFieldKind.VOLUME_NAME, ParsingErrorCode.INVALID_MYSQL_VOLUME_NAME, "volumeName"),
    MYSQL_ROOT_PASSWORD(ComponentType.MYSQL, PropertyFieldKind.ACCOUNT, ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER, "env", "rootPassword"),
    MYSQL_USERNAME(ComponentType.MYSQL, PropertyFieldKind.ACCOUNT, ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER, "env", "username"),
    MYSQL_USER_PASSWORD(ComponentType.MYSQL, PropertyFieldKind.ACCOUNT, ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER, "env", "userPassword"),

    // PostgreSQL
    POSTGRESQL_IMAGE_VERSION(ComponentType.POSTGRESQL, PropertyFieldKind.IMAGE_VERSION, ParsingErrorCode.INVALID_POSTGRES_IMAGE_VERSION, "imageVersion"),
    POSTGRESQL_CONTAINER_NAME(ComponentType.POSTGRESQL, PropertyFieldKind.CONTAINER_NAME, ParsingErrorCode.INVALID_POSTGRES_CONTAINER_NAME, "containerName"),
    POSTGRESQL_VOLUME_NAME(ComponentType.POSTGRESQL, PropertyFieldKind.VOLUME_NAME, ParsingErrorCode.INVALID_POSTGRES_VOLUME_NAME, "volumeName"),
    POSTGRESQL_PASSWORD(ComponentType.POSTGRESQL, PropertyFieldKind.ACCOUNT, ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER, "env", "password"),
    POSTGRESQL_USERNAME(ComponentType.POSTGRESQL, PropertyFieldKind.ACCOUNT, ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER, "env", "username"),

    // MongoDB
    MONGODB_IMAGE_VERSION(ComponentType.MONGODB, PropertyFieldKind.IMAGE_VERSION, ParsingErrorCode.INVALID_MONGODB_IMAGE_VERSION, "imageVersion"),
    MONGODB_CONTAINER_NAME(ComponentType.MONGODB, PropertyFieldKind.CONTAINER_NAME, ParsingErrorCode.INVALID_MONGODB_CONTAINER_NAME, "containerName"),
    MONGODB_VOLUME_NAME(ComponentType.MONGODB, PropertyFieldKind.VOLUME_NAME, ParsingErrorCode.INVALID_MONGODB_VOLUME_NAME, "volumeName"),
    MONGODB_PASSWORD(ComponentType.MONGODB, PropertyFieldKind.ACCOUNT, ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER, "env", "password"),
    MONGODB_USERNAME(ComponentType.MONGODB, PropertyFieldKind.ACCOUNT, ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER, "env", "username"),

    // Redis
    REDIS_IMAGE_VERSION(ComponentType.REDIS, PropertyFieldKind.IMAGE_VERSION, ParsingErrorCode.INVALID_REDIS_IMAGE_VERSION, "imageVersion"),
    REDIS_CONTAINER_NAME(ComponentType.REDIS, PropertyFieldKind.CONTAINER_NAME, ParsingErrorCode.INVALID_REDIS_CONTAINER_NAME, "containerName"),
    REDIS_VOLUME_NAME(ComponentType.REDIS, PropertyFieldKind.VOLUME_NAME, ParsingErrorCode.INVALID_REDIS_VOLUME_NAME, "volumeName"),
    REDIS_PASSWORD(ComponentType.REDIS, PropertyFieldKind.ACCOUNT, ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER, "password"),

    // Caddy
    CADDY_IMAGE_VERSION(ComponentType.CADDY, PropertyFieldKind.IMAGE_VERSION, ParsingErrorCode.INVALID_CADDY_IMAGE_VERSION, "imageVersion"),
    CADDY_VOLUME_NAME(ComponentType.CADDY, PropertyFieldKind.VOLUME_NAME, ParsingErrorCode.INVALID_CADDY_VOLUME_NAME, "volumeName");

    private final ComponentType componentType;
    private final PropertyFieldKind kind;
    private final ParsingErrorCode errorCode;
    private final String[] path;

    ComponentPropertyField(
        ComponentType componentType,
        PropertyFieldKind kind,
        ParsingErrorCode errorCode,
        String... path
    ) {
        this.componentType = componentType;
        this.kind = kind;
        this.errorCode = errorCode;
        this.path = path;
    }

    public ComponentType componentType() {
        return componentType;
    }

    public PropertyFieldKind kind() {
        return kind;
    }

    public ParsingErrorCode errorCode() {
        return errorCode;
    }

    /**
     * {@code properties}에서 이 필드의 값을 문자열로 읽는다.
     *
     * <p>없는 키는 빈 문자열을 반환한다.
     */
    public String read(JsonNode props) {
        if (props == null) {
            return "";
        }
        JsonNode node = props;
        for (String key : path) {
            node = node.path(key);
        }
        return node.asString();
    }

    /**
     * 대상 컴포넌트 타입의 속성 필드 목록을 선언 순서대로 반환한다.
     *
     * <p>대상이 없거나 null인 경우 빈 목록을 반환한다.
     */
    public static List<ComponentPropertyField> of(ComponentType componentType) {
        if (componentType == null) {
            return List.of();
        }
        return Arrays.stream(values())
            .filter(field -> field.componentType == componentType)
            .toList();
    }
}
