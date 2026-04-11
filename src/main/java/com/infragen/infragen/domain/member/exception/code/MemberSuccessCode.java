package com.infragen.infragen.domain.member.exception.code;

import com.infragen.infragen.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MemberSuccessCode implements BaseSuccessCode {
    ,;

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
