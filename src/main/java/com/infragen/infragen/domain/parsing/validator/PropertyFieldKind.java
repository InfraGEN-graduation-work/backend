package com.infragen.infragen.domain.parsing.validator;

import java.util.function.Predicate;

/**
 * 노드 {@code properties}에서 형식 검증 대상이 되는 필드의 분류를 정의한다.
 *
 * <p>필드 종류마다 적용되는 형식 검증 규칙(정규식, 금지 문자 등)과 오류 성격을 식별하는 데 사용한다.
 */
public enum PropertyFieldKind {

    /**
     * 사용자 이름, 비밀번호 등 계정 설정 값.
     */
    ACCOUNT {
        @Override
        public boolean isValid(String value) {
            return value == null || !EnvValueValidator.hasForbiddenCharacter(value);
        }
    },

    /**
     * Docker 이미지 참조 (이름, 태그, 다이제스트).
     */
    IMAGE_VERSION {
        @Override
        public boolean isValid(String value) {
            return isBlankOrValid(value, ComposeYamlValueValidator::isImageReference);
        }
    },

    /**
     * Docker 볼륨 이름.
     */
    VOLUME_NAME {
        @Override
        public boolean isValid(String value) {
            return isBlankOrValid(value, ComposeYamlValueValidator::isVolumeName);
        }
    },

    /**
     * Docker 컨테이너 이름.
     */
    CONTAINER_NAME {
        @Override
        public boolean isValid(String value) {
            return isBlankOrValid(value, ComposeYamlValueValidator::isContainerName);
        }
    };

    /**
     * 주어진 문자열 값이 이 필드 종류의 형식 규칙을 만족하는지 검증한다.
     *
     * <p>필수값 누락(null 또는 blank)은 이 검증기의 책임이 아니므로 {@code true}로 통과시키며,
     * 값이 존재할 때만 각 종류별 형식 적합성(금지 문자, 명명 규칙 등)을 검사한다.
     *
     * @param value 검사할 문자열 값 (null 또는 공백 문자열 허용)
     * @return 값이 유효하거나 빈 값이면 {@code true}, 형식 규칙을 위반하면 {@code false}
     */
    public abstract boolean isValid(String value);

    private static boolean isBlankOrValid(String value, Predicate<String> predicate) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return predicate.test(value.trim());
    }
}
