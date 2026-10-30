package com.infragen.infragen.domain.parsing.validator;

import com.infragen.infragen.domain.parsing.exception.ComponentPropertyValidationException;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;

/**
 * 컴포넌트 속성 값의 형식(계정 금지 문자, 이미지 참조, 볼륨 이름, 컨테이너 이름)을 검증한다.
 *
 * <p>필수값 누락(빈 값)은 검증하지 않고 값이 존재할 때만 형식 적합성을 검사하여, 미완성 노드도 통과할 수 있도록 한다.
 */
public final class ComponentPropertyValidator {

    private ComponentPropertyValidator() {
    }

    /**
     * 컴포넌트 타입의 선언된 모든 속성 필드를 검사한다.
     *
     * <p>필수값 누락과 길이는 검사하지 않으며, 형식을 위반한 필드를 처음 발견했을 때 {@link ComponentPropertyValidationException}을 던진다.
     * 대상 컴포넌트 타입 또는 {@code props}가 null이거나 검사 대상이 없는 경우 통과한다.
     *
     * @param componentType 검사할 컴포넌트 타입
     * @param props 노드 속성 JSON
     * @throws ComponentPropertyValidationException 속성 형식을 위반한 경우
     */
    public static void validateProperties(ComponentType componentType, JsonNode props) {
        if (componentType == null || props == null) {
            return;
        }

        for (ComponentPropertyField field : ComponentPropertyField.of(componentType)) {
            String rawValue = field.read(props);
            validateField(field, rawValue);
        }
    }

    private static void validateField(ComponentPropertyField field, String rawValue) {
        if (!field.kind().isValid(rawValue)) {
            throw new ComponentPropertyValidationException(field.kind(), field.errorCode());
        }
    }
}
