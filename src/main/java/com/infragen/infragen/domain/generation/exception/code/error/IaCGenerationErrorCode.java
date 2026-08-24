package com.infragen.infragen.domain.generation.exception.code.error;

import org.springframework.http.HttpStatus;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum IaCGenerationErrorCode implements BaseErrorCode {

    UNSUPPORTED_OUTPUT_FORMAT(
        HttpStatus.BAD_REQUEST,
        "지원하지 않는 출력 형식입니다.",
        "GENERATION400_1"
    ),
    INVALID_COMPONENT_STATE(
        HttpStatus.BAD_REQUEST,
        "인프라 코드 생성에 필요한 컴포넌트 상태가 올바르지 않습니다.",
        "GENERATION400_2"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
