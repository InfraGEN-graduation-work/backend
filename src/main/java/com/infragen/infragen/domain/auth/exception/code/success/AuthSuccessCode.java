package com.infragen.infragen.domain.auth.exception.code.success;

import com.infragen.infragen.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {
    EMAIL_CODE_SEND_SUCCESS(HttpStatus.OK, "인증 메일을 발송했습니다.", "AUTH200_4"),
    SIGNUP_SUCCESS(HttpStatus.CREATED, "회원가입에 성공했습니다.", "AUTH201_1"),
    LOGIN_SUCCESS(HttpStatus.OK, "로그인에 성공했습니다.", "AUTH200_1"),
    GUEST_LOGIN_SUCCESS(HttpStatus.OK, "게스트 로그인에 성공했습니다.", "AUTH200_2"),
    TOKEN_REFRESH_SUCCESS(HttpStatus.OK, "토큰 재발급에 성공했습니다.", "AUTH200_3");

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
