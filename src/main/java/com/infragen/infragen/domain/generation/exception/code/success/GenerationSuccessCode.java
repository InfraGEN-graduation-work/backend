package com.infragen.infragen.domain.generation.exception.code.success;

import org.springframework.http.HttpStatus;

import com.infragen.infragen.global.apiPayload.code.BaseSuccessCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GenerationSuccessCode implements BaseSuccessCode {

    GENERATE_SUCCESS(
        HttpStatus.OK,
        "인프라 코드 생성에 성공했습니다.",
        "GENERATION200_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
