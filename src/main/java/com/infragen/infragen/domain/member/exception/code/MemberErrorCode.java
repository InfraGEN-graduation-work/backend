package com.infragen.infragen.domain.member.exception.code;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MemberErrorCode implements BaseErrorCode {
    ,;
    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
