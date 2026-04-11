package com.infragen.infragen.domain.auth.exception.code;

import com.infragen.infragen.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {
    AUTH_REISSUE_OK(HttpStatus.OK,
            "토큰 재발행을 완료하였습니다.",
            "AUTH200_1"
    ),
    ;

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
