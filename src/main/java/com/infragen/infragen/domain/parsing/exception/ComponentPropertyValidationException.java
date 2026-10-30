package com.infragen.infragen.domain.parsing.exception;

import com.infragen.infragen.domain.parsing.validator.PropertyFieldKind;
import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;

/**
 * 노드 속성의 형식 값(계정 값, 이미지 버전, 볼륨 이름, 컨테이너 이름) 검증 실패 시 발생하는 예외다.
 *
 * <p>어떤 종류의 속성 필드에서 위반이 발생했는지 식별할 수 있도록 {@link PropertyFieldKind}를 보관한다.
 */
public class ComponentPropertyValidationException extends ParsingException {

    private final PropertyFieldKind kind;

    public ComponentPropertyValidationException(PropertyFieldKind kind, BaseErrorCode errorCode) {
        super(errorCode);
        this.kind = kind;
    }

    public PropertyFieldKind getKind() {
        return kind;
    }
}
