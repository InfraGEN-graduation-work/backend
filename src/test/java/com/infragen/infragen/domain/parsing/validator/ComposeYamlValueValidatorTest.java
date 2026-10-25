package com.infragen.infragen.domain.parsing.validator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
    @MethodSource("repositoryPathWithinLimit")
    @DisplayName("Docker 정규화 후 저장소 경로가 255자 이하 — true")
    void isImageReference_RepositoryPathWithinLimit_ReturnsTrue(String value) {
        // when
        boolean result = ComposeYamlValueValidator.isImageReference(value);

        // then
        assertTrue(result);
    }

    @ParameterizedTest
    @MethodSource("repositoryPathOverLimit")
    @DisplayName("Docker 정규화 후 저장소 경로가 255자 초과 — false")
    void isImageReference_RepositoryPathOverLimit_ReturnsFalse(String value) {
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

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "my-db.1", "DB_1", "a1"})
    @DisplayName("Docker 컨테이너 이름 규칙 — true")
    void isContainerName_ValidName_ReturnsTrue(String value) {
        // when
        boolean result = ComposeYamlValueValidator.isContainerName(value);

        // then
        assertTrue(result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "db\n    privileged: true", "my db", " mysql", "db:1", "db/1", "${NAME}", "_db", ".db", "-db", "a"
    })
    @DisplayName("컨테이너 이름 규칙을 어기거나 1자인 값 — false")
    void isContainerName_InvalidName_ReturnsFalse(String value) {
        // when
        boolean result = ComposeYamlValueValidator.isContainerName(value);

        // then
        assertFalse(result);
    }

    // 도메인이 없고 '/'도 없는 이름은 Docker가 "library/"(8자)를 붙여 경로를 잰다. 태그와 다이제스트, 레지스트리 도메인은 길이에서 빠진다.
    private static Stream<String> repositoryPathWithinLimit() {
        return Stream.of(
            "a".repeat(247),
            "a".repeat(247) + ":tag",
            "a".repeat(247) + "@sha256:" + "a".repeat(64),
            "org/" + "a".repeat(251),
            "ghcr.io/" + "a".repeat(255),
            "localhost:5000/" + "a".repeat(255),
            "docker.io/" + "a".repeat(247)
        );
    }

    private static Stream<String> repositoryPathOverLimit() {
        return Stream.of(
            "a".repeat(248),
            "org/" + "a".repeat(252),
            "ghcr.io/" + "a".repeat(256),
            "localhost:5000/" + "a".repeat(256),
            "docker.io/" + "a".repeat(248),
            "index.docker.io/" + "a".repeat(248)
        );
    }
}
