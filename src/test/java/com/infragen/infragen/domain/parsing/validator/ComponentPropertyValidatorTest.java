package com.infragen.infragen.domain.parsing.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.infragen.infragen.domain.parsing.exception.ComponentPropertyValidationException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("컴포넌트 속성 형식 검증기")
class ComponentPropertyValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @MethodSource("forbiddenAccountFieldProperties")
    @DisplayName("계정 값 필드에 금지 문자 포함 시 ACCOUNT 분류와 UNSUPPORTED_ENV_VALUE_CHARACTER 예외를 던진다")
    void validateProperties_ForbiddenCharacterInAccountField_ThrowsValidationException(
        ComponentType componentType,
        String propertiesJson
    ) {
        // given
        JsonNode props = objectMapper.readTree(propertiesJson);

        // when
        ComponentPropertyValidationException exception = assertThrows(
            ComponentPropertyValidationException.class,
            () -> ComponentPropertyValidator.validateProperties(componentType, props)
        );

        // then
        assertEquals(PropertyFieldKind.ACCOUNT, exception.getKind());
        assertEquals(ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER, exception.getCode());
    }

    @ParameterizedTest
    @MethodSource("invalidImageVersionProperties")
    @DisplayName("이미지 버전 형식이 올바르지 않으면 IMAGE_VERSION 분류와 해당 에러코드를 던진다")
    void validateProperties_InvalidImageVersion_ThrowsValidationException(
        ComponentType componentType,
        ParsingErrorCode expectedErrorCode
    ) {
        // given
        JsonNode props = objectMapper.readTree("{\"imageVersion\": \"invalid:image:tag:extra\"}");

        // when
        ComponentPropertyValidationException exception = assertThrows(
            ComponentPropertyValidationException.class,
            () -> ComponentPropertyValidator.validateProperties(componentType, props)
        );

        // then
        assertEquals(PropertyFieldKind.IMAGE_VERSION, exception.getKind());
        assertEquals(expectedErrorCode, exception.getCode());
    }

    @ParameterizedTest
    @MethodSource("invalidVolumeNameProperties")
    @DisplayName("볼륨 이름 형식이 올바르지 않으면 VOLUME_NAME 분류와 해당 에러코드를 던진다")
    void validateProperties_InvalidVolumeName_ThrowsValidationException(
        ComponentType componentType,
        ParsingErrorCode expectedErrorCode
    ) {
        // given
        JsonNode props = objectMapper.readTree("{\"volumeName\": \"-invalid_volume\"}");

        // when
        ComponentPropertyValidationException exception = assertThrows(
            ComponentPropertyValidationException.class,
            () -> ComponentPropertyValidator.validateProperties(componentType, props)
        );

        // then
        assertEquals(PropertyFieldKind.VOLUME_NAME, exception.getKind());
        assertEquals(expectedErrorCode, exception.getCode());
    }

    @ParameterizedTest
    @MethodSource("invalidContainerNameProperties")
    @DisplayName("컨테이너 이름 형식이 올바르지 않으면 CONTAINER_NAME 분류와 해당 에러코드를 던진다")
    void validateProperties_InvalidContainerName_ThrowsValidationException(
        ComponentType componentType,
        ParsingErrorCode expectedErrorCode
    ) {
        // given
        JsonNode props = objectMapper.readTree("{\"containerName\": \"-invalid_container\"}");

        // when
        ComponentPropertyValidationException exception = assertThrows(
            ComponentPropertyValidationException.class,
            () -> ComponentPropertyValidator.validateProperties(componentType, props)
        );

        // then
        assertEquals(PropertyFieldKind.CONTAINER_NAME, exception.getKind());
        assertEquals(expectedErrorCode, exception.getCode());
    }

    @ParameterizedTest
    @EnumSource(value = ComponentType.class, names = {"MYSQL", "POSTGRESQL", "MONGODB", "REDIS", "CADDY"})
    @DisplayName("미완성 노드 또는 빈 값, 공백 값은 누락 검사를 하지 않으므로 통과한다")
    void validateProperties_EmptyOrBlankValues_DoesNotThrow(ComponentType componentType) {
        // given
        JsonNode emptyProps = objectMapper.readTree("{}");
        JsonNode blankProps = objectMapper.readTree("""
            {
                "imageVersion": "   ",
                "containerName": "   ",
                "volumeName": "   ",
                "password": "",
                "env": {
                    "rootPassword": "",
                    "userPassword": "   ",
                    "password": "",
                    "username": ""
                }
            }
            """);

        // when & then
        assertDoesNotThrow(() -> ComponentPropertyValidator.validateProperties(componentType, emptyProps));
        assertDoesNotThrow(() -> ComponentPropertyValidator.validateProperties(componentType, blankProps));
    }

    @Test
    @DisplayName("Caddy 노드는 컨테이너 이름을 검사하지 않으므로 부적합한 컨테이너 이름이 있어도 통과한다")
    void validateProperties_CaddyIgnoresContainerName_DoesNotThrow() {
        // given
        JsonNode props = objectMapper.readTree("{\"containerName\": \"-invalid_container_name\"}");

        // when & then
        assertDoesNotThrow(() -> ComponentPropertyValidator.validateProperties(ComponentType.CADDY, props));
    }

    @Test
    @DisplayName("null 인자 또는 대상 필드가 없는 타입은 검증을 건너뛰고 예외를 던지지 않는다")
    void validateProperties_NullOrUnsupportedType_DoesNotThrow() {
        // given
        JsonNode props = objectMapper.readTree("{\"imageVersion\": \"-invalid_image\"}");

        // when & then
        assertDoesNotThrow(() -> ComponentPropertyValidator.validateProperties(null, props));
        assertDoesNotThrow(() -> ComponentPropertyValidator.validateProperties(ComponentType.MYSQL, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "mysql:8.0",
        "postgres:15-alpine",
        "mongo:6.0",
        "redis:7.0-alpine",
        "caddy:2.7-alpine"
    })
    @DisplayName("정상적인 속성 값들로 구성된 노드는 통과한다")
    void validateProperties_ValidProperties_DoesNotThrow(String image) {
        // given
        JsonNode props = objectMapper.readTree(String.format("""
            {
                "imageVersion": "%s",
                "containerName": "valid-container-1",
                "volumeName": "valid_volume_1",
                "password": "valid_password_1",
                "env": {
                    "rootPassword": "root_password_1",
                    "userPassword": "user_password_1",
                    "password": "valid_password_1",
                    "username": "valid_user_1"
                }
            }
            """, image));

        // when & then
        assertDoesNotThrow(() -> ComponentPropertyValidator.validateProperties(ComponentType.MYSQL, props));
        assertDoesNotThrow(() -> ComponentPropertyValidator.validateProperties(ComponentType.POSTGRESQL, props));
        assertDoesNotThrow(() -> ComponentPropertyValidator.validateProperties(ComponentType.MONGODB, props));
        assertDoesNotThrow(() -> ComponentPropertyValidator.validateProperties(ComponentType.REDIS, props));
        assertDoesNotThrow(() -> ComponentPropertyValidator.validateProperties(ComponentType.CADDY, props));
    }

    private static Stream<Arguments> forbiddenAccountFieldProperties() {
        return Stream.of(
            Arguments.of(ComponentType.MYSQL, "{\"env\": {\"rootPassword\": \"a$b\"}}"),
            Arguments.of(ComponentType.MYSQL, "{\"env\": {\"username\": \"a\\\"b\"}}"),
            Arguments.of(ComponentType.MYSQL, "{\"env\": {\"userPassword\": \"a\\nb\"}}"),
            Arguments.of(ComponentType.POSTGRESQL, "{\"env\": {\"password\": \"a$b\"}}"),
            Arguments.of(ComponentType.POSTGRESQL, "{\"env\": {\"username\": \"a\\\\b\"}}"),
            Arguments.of(ComponentType.MONGODB, "{\"env\": {\"password\": \"a$b\"}}"),
            Arguments.of(ComponentType.MONGODB, "{\"env\": {\"username\": \"a\\rb\"}}"),
            Arguments.of(ComponentType.REDIS, "{\"password\": \"a$b\"}")
        );
    }

    private static Stream<Arguments> invalidImageVersionProperties() {
        return Stream.of(
            Arguments.of(ComponentType.MYSQL, ParsingErrorCode.INVALID_MYSQL_IMAGE_VERSION),
            Arguments.of(ComponentType.POSTGRESQL, ParsingErrorCode.INVALID_POSTGRES_IMAGE_VERSION),
            Arguments.of(ComponentType.MONGODB, ParsingErrorCode.INVALID_MONGODB_IMAGE_VERSION),
            Arguments.of(ComponentType.REDIS, ParsingErrorCode.INVALID_REDIS_IMAGE_VERSION),
            Arguments.of(ComponentType.CADDY, ParsingErrorCode.INVALID_CADDY_IMAGE_VERSION)
        );
    }

    private static Stream<Arguments> invalidVolumeNameProperties() {
        return Stream.of(
            Arguments.of(ComponentType.MYSQL, ParsingErrorCode.INVALID_MYSQL_VOLUME_NAME),
            Arguments.of(ComponentType.POSTGRESQL, ParsingErrorCode.INVALID_POSTGRES_VOLUME_NAME),
            Arguments.of(ComponentType.MONGODB, ParsingErrorCode.INVALID_MONGODB_VOLUME_NAME),
            Arguments.of(ComponentType.REDIS, ParsingErrorCode.INVALID_REDIS_VOLUME_NAME),
            Arguments.of(ComponentType.CADDY, ParsingErrorCode.INVALID_CADDY_VOLUME_NAME)
        );
    }

    private static Stream<Arguments> invalidContainerNameProperties() {
        return Stream.of(
            Arguments.of(ComponentType.MYSQL, ParsingErrorCode.INVALID_MYSQL_CONTAINER_NAME),
            Arguments.of(ComponentType.POSTGRESQL, ParsingErrorCode.INVALID_POSTGRES_CONTAINER_NAME),
            Arguments.of(ComponentType.MONGODB, ParsingErrorCode.INVALID_MONGODB_CONTAINER_NAME),
            Arguments.of(ComponentType.REDIS, ParsingErrorCode.INVALID_REDIS_CONTAINER_NAME)
        );
    }
}
