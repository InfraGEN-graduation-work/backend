package com.infragen.infragen.domain.parsing.exception.code.success;

import com.infragen.infragen.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ParsingSuccessCode implements BaseSuccessCode {

    PARSING_SUCCESS(
            HttpStatus.OK,
            "인프라 아키텍처 파싱에 성공했습니다.",
            "PARSING200_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}