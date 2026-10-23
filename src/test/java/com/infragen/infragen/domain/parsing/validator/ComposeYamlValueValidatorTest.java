package com.infragen.infragen.domain.parsing.validator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Compose 이미지 참조와 볼륨 이름 형식 판정")
class ComposeYamlValueValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "caddy", "caddy:2.8-alpine", "ghcr.io/org/caddy:2", "localhost:5000/caddy:2",
        "caddy@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "caddy:2.8@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    })
    @DisplayName("Docker 이미지 참조 형식 — true")
    void isImageReference_ValidReference_ReturnsTrue(String value) {
        // when
        boolean result = ComposeYamlValueValidator.isImageReference(value);

        // then
        assertTrue(result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "caddy:2\n    privileged: true", "caddy 2", " caddy:2", "${IMAGE}", "-caddy", "caddy:2\"", "caddy#2",
        "caddy:", "caddy@", ":2", "caddy::2", "caddy:2:3", "caddy:-1", "Caddy:2", "caddy@sha256:abc123",
        "caddy@sha256:",
        "caddy@:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "caddy@sha256aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    })
    @DisplayName("YAML 구조를 바꾸거나 이미지 참조 구조가 아닌 값 — false")
    void isImageReference_InvalidReference_ReturnsFalse(String value) {
        // when
        boolean result = ComposeYamlValueValidator.isImageReference(value);

        // then
        assertFalse(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "caddy@sha384:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "caddy@sha512:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    })
    @DisplayName("Docker가 지원하는 다이제스트 알고리즘과 길이 — true")
    void isImageReference_SupportedDigest_ReturnsTrue(String value) {
        // when
        boolean result = ComposeYamlValueValidator.isImageReference(value);

        // then
        assertTrue(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "caddy@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "caddy@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "caddy@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "caddy@sha256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "caddy@sha384:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "caddy@sha512:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "caddy@foo:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    })
    @DisplayName("알고리즘별 길이가 다르거나 대문자이거나 지원하지 않는 알고리즘인 다이제스트 — false")
    void isImageReference_UnsupportedDigest_ReturnsFalse(String value) {
        // when
        boolean result = ComposeYamlValueValidator.isImageReference(value);

        // then
        assertFalse(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"caddy_data", "caddy-data.1", "Data1"})
    @DisplayName("Docker 볼륨 이름 규칙 — true")
    void isVolumeName_ValidName_ReturnsTrue(String value) {
        // when
        boolean result = ComposeYamlValueValidator.isVolumeName(value);

        // then
        assertTrue(result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "caddy@data", "certs:/etc", "caddy data", " caddy_data", "caddy\ndata", "_data", ".data", "-data", "${VOL}"
    })
    @DisplayName("볼륨 이름 규칙을 어긴 값 — false")
    void isVolumeName_InvalidName_ReturnsFalse(String value) {
        // when
        boolean result = ComposeYamlValueValidator.isVolumeName(value);

        // then
        assertFalse(result);
    }
}
