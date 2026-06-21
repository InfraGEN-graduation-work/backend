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
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
