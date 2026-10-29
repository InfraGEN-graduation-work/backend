package com.infragen.infragen.domain.parsing.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName(".env 값 금지 문자 검증")
class EnvValueValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"a\"b", "a\\b", "a$b", "a\nb", "a\rb", "a\0b"})
    @DisplayName("금지 문자 포함 — UNSUPPORTED_ENV_VALUE_CHARACTER")
    void validate_ForbiddenCharacter_ThrowsParsingException(String value) {
        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> EnvValueValidator.validate(value)
        );

        // then
        assertEquals(
            ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc12345", "a!@#%^&*b", "a b", "a'b", "a`b", "한글비밀번호"})
    @DisplayName("허용 문자 — 통과")
    void validate_AllowedCharacters_DoesNotThrow(String value) {
        // when & then
        assertDoesNotThrow(() -> EnvValueValidator.validate(value));
    }

    @Test
    @DisplayName("null과 빈 값 — 기존 검사가 처리하므로 통과")
    void validate_NullOrEmpty_DoesNotThrow() {
        // when & then
        assertDoesNotThrow(() -> EnvValueValidator.validate(null));
        assertDoesNotThrow(() -> EnvValueValidator.validate(""));
    }

    @ParameterizedTest
    @MethodSource("forbiddenFieldProperties")
    @DisplayName("properties의 계정 값 필드에 금지 문자 — UNSUPPORTED_ENV_VALUE_CHARACTER")
    void validateProperties_ForbiddenCharacterInField_ThrowsParsingException(
        ComponentType componentType,
        String propertiesJson
    ) {
        // given
        JsonNode props = objectMapper.readTree(propertiesJson);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> EnvValueValidator.validateProperties(componentType, props)
        );

        // then
        assertEquals(
            ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @EnumSource(value = ComponentType.class, names = {"MYSQL", "POSTGRESQL", "MONGODB", "REDIS"})
    @DisplayName("properties가 비었거나 키가 없음 — 누락은 보지 않으므로 통과")
    void validateProperties_MissingKeys_DoesNotThrow(ComponentType componentType) {
        // given
        JsonNode props = objectMapper.readTree("{}");

        // when & then
        assertDoesNotThrow(() -> EnvValueValidator.validateProperties(componentType, props));
    }

    @ParameterizedTest
    @EnumSource(value = ComponentType.class, names = {"MYSQL", "POSTGRESQL", "MONGODB", "REDIS"})
    @DisplayName("계정 값이 허용 문자뿐 — 통과")
    void validateProperties_AllowedCharacters_DoesNotThrow(ComponentType componentType) {
        // given
        JsonNode props = objectMapper.readTree("""
            {"password": "pass!@#word", "env": {"rootPassword": "root-pass1", "userPassword": "user-pass1",
             "password": "pass!@#word", "username": "app_user"}}
            """);

        // when & then
        assertDoesNotThrow(() -> EnvValueValidator.validateProperties(componentType, props));
    }

    @Test
    @DisplayName("검사 대상이 없는 타입 — 같은 키에 금지 문자가 있어도 통과")
    void validateProperties_TypeWithoutFields_DoesNotThrow() {
        // given
        JsonNode props = objectMapper.readTree("{\"password\": \"a$b\", \"env\": {\"password\": \"a$b\"}}");

        // when & then
        assertDoesNotThrow(() -> EnvValueValidator.validateProperties(ComponentType.CADDY, props));
    }

    @Test
    @DisplayName("필드 목록 — MySQL은 루트 비밀번호, 사용자 이름, 사용자 비밀번호 순서")
    void of_MySql_ReturnsFieldsInDeclaredOrder() {
        // when
        List<EnvValueField> fields = EnvValueField.of(ComponentType.MYSQL);

        // then
        assertEquals(
            List.of(
                EnvValueField.MYSQL_ROOT_PASSWORD,
                EnvValueField.MYSQL_USERNAME,
                EnvValueField.MYSQL_USER_PASSWORD
            ),
            fields
        );
    }

    @Test
    @DisplayName("필드 읽기 — 있는 키는 값을, 없는 키는 빈 문자열을 돌려준다")
    void read_PresentAndMissingKey_ReturnsValueOrEmpty() {
        // given
        JsonNode props = objectMapper.readTree("{\"env\": {\"password\": \"pg-pass\"}}");

        // when & then
        assertEquals("pg-pass", EnvValueField.POSTGRESQL_PASSWORD.read(props));
        assertEquals("", EnvValueField.POSTGRESQL_USERNAME.read(props));
        assertEquals("", EnvValueField.REDIS_PASSWORD.read(props));
    }

    // 파서가 읽는 위치를 그대로 적어 enum의 키가 바뀌면 이 목록이 깨지게 한다.
    private static Stream<Arguments> forbiddenFieldProperties() {
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
}
