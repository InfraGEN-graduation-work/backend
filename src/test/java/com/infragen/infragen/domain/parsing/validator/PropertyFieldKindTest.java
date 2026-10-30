package com.infragen.infragen.domain.parsing.validator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("속성 필드 종류별 유효성 검증")
class PropertyFieldKindTest {

    @ParameterizedTest
    @ValueSource(strings = {"validPassword123!", "valid_user", "한글비밀번호", "pass word"})
    @DisplayName("ACCOUNT — 허용된 문자열은 유효하다")
    void isValid_AccountWithAllowedCharacters_ReturnsTrue(String value) {
        // given: 유효한 계정 문자열

        // when
        boolean result = PropertyFieldKind.ACCOUNT.isValid(value);

        // then
        assertTrue(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\"b", "a\\b", "a$b", "a\nb", "a\rb", "a\0b"})
    @DisplayName("ACCOUNT — 금지 문자 포함 시 유효하지 않다")
    void isValid_AccountWithForbiddenCharacters_ReturnsFalse(String value) {
        // given: 금지 문자가 포함된 문자열

        // when
        boolean result = PropertyFieldKind.ACCOUNT.isValid(value);

        // then
        assertFalse(result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("ACCOUNT — null 또는 빈 값은 유효하다")
    void isValid_AccountNullOrBlank_ReturnsTrue(String value) {
        // given: null 또는 공백 문자열

        // when
        boolean result = PropertyFieldKind.ACCOUNT.isValid(value);

        // then
        assertTrue(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql:8.0", "postgres:15-alpine", "mongo:6.0", "redis:7.0", "caddy:2.7-alpine"})
    @DisplayName("IMAGE_VERSION — 유효한 이미지 참조는 유효하다")
    void isValid_ValidImageVersion_ReturnsTrue(String value) {
        // given: 유효한 이미지 참조 문자열

        // when
        boolean result = PropertyFieldKind.IMAGE_VERSION.isValid(value);

        // then
        assertTrue(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid:image:tag:extra", "-invalid_image", "image with space"})
    @DisplayName("IMAGE_VERSION — 부적합한 이미지 참조 형식은 유효하지 않다")
    void isValid_InvalidImageVersion_ReturnsFalse(String value) {
        // given: 부적합한 이미지 참조 문자열

        // when
        boolean result = PropertyFieldKind.IMAGE_VERSION.isValid(value);

        // then
        assertFalse(result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("IMAGE_VERSION — null 또는 공백 값은 유효하다")
    void isValid_ImageVersionNullOrBlank_ReturnsTrue(String value) {
        // given: null 또는 공백 문자열

        // when
        boolean result = PropertyFieldKind.IMAGE_VERSION.isValid(value);

        // then
        assertTrue(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"my-volume", "valid_volume_1", "db_data"})
    @DisplayName("VOLUME_NAME — 유효한 볼륨 이름은 유효하다")
    void isValid_ValidVolumeName_ReturnsTrue(String value) {
        // given: 유효한 볼륨 이름 문자열

        // when
        boolean result = PropertyFieldKind.VOLUME_NAME.isValid(value);

        // then
        assertTrue(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-invalid_volume", "invalid/volume", "volume with space"})
    @DisplayName("VOLUME_NAME — 부적합한 볼륨 이름 형식은 유효하지 않다")
    void isValid_InvalidVolumeName_ReturnsFalse(String value) {
        // given: 부적합한 볼륨 이름 문자열

        // when
        boolean result = PropertyFieldKind.VOLUME_NAME.isValid(value);

        // then
        assertFalse(result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("VOLUME_NAME — null 또는 공백 값은 유효하다")
    void isValid_VolumeNameNullOrBlank_ReturnsTrue(String value) {
        // given: null 또는 공백 문자열

        // when
        boolean result = PropertyFieldKind.VOLUME_NAME.isValid(value);

        // then
        assertTrue(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"my-container", "valid-container-1", "app_1"})
    @DisplayName("CONTAINER_NAME — 유효한 컨테이너 이름은 유효하다")
    void isValid_ValidContainerName_ReturnsTrue(String value) {
        // given: 유효한 컨테이너 이름 문자열

        // when
        boolean result = PropertyFieldKind.CONTAINER_NAME.isValid(value);

        // then
        assertTrue(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-invalid_container", "invalid/container", "container with space"})
    @DisplayName("CONTAINER_NAME — 부적합한 컨테이너 이름 형식은 유효하지 않다")
    void isValid_InvalidContainerName_ReturnsFalse(String value) {
        // given: 부적합한 컨테이너 이름 문자열

        // when
        boolean result = PropertyFieldKind.CONTAINER_NAME.isValid(value);

        // then
        assertFalse(result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("CONTAINER_NAME — null 또는 공백 값은 유효하다")
    void isValid_ContainerNameNullOrBlank_ReturnsTrue(String value) {
        // given: null 또는 공백 문자열

        // when
        boolean result = PropertyFieldKind.CONTAINER_NAME.isValid(value);

        // then
        assertTrue(result);
    }

    @ParameterizedTest
    @EnumSource(PropertyFieldKind.class)
    @DisplayName("모든 필드 종류 — null 값은 유효성 검사를 통과한다")
    void isValid_AllKindsWithNull_ReturnsTrue(PropertyFieldKind kind) {
        // given
        String value = null;

        // when
        boolean result = kind.isValid(value);

        // then
        assertTrue(result);
    }

    @ParameterizedTest
    @EnumSource(PropertyFieldKind.class)
    @DisplayName("모든 필드 종류 — 공백 문자열은 유효성 검사를 통과한다")
    void isValid_AllKindsWithBlank_ReturnsTrue(PropertyFieldKind kind) {
        // given
        String value = "   ";

        // when
        boolean result = kind.isValid(value);

        // then
        assertTrue(result);
    }
}
