package com.infragen.infragen.domain.parsing.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;

@DisplayName(".env 값 금지 문자 검증")
class EnvValueValidatorTest {

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
}
