package com.infragen.infragen.domain.auth.exception.code.success;

import com.infragen.infragen.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {
    SIGNUP_SUCCESS(HttpStatus.CREATED, "회원가입에 성공했습니다.", "AUTH201_1"),
    LOGIN_SUCCESS(HttpStatus.OK, "로그인에 성공했습니다.", "AUTH200_1"),
    TOKEN_REFRESH_SUCCESS(HttpStatus.OK, "토큰 재발급에 성공했습니다.", "AUTH200_3");

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
