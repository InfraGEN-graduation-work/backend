package com.infragen.infragen.domain.auth.exception.code.error;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;


@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
    EMAIL_CODE_INVALID(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않거나 만료되었습니다.", "AUTH400_3"),
    EMAIL_CODE_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "인증 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.", "AUTH429_1"),
    EMAIL_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "인증 메일을 발송하지 못했습니다. 잠시 후 다시 시도해주세요.", "AUTH503_1"),
    TOKEN_INVALID(
        HttpStatus.UNAUTHORIZED,
        "유효하지 않은 토큰입니다.",
        "AUTH401_1"
    ),
    TOKEN_BLACKLIST(
        HttpStatus.UNAUTHORIZED,
        "로그아웃된 토큰입니다.",
        "AUTH401_2"
    ),
    TOKEN_EXPIRED(
            HttpStatus.UNAUTHORIZED,
            "만료된 토큰입니다.",
            "AUTH401_3"
    ),
    INVALID_SOCIAL_TOKEN(
        HttpStatus.UNAUTHORIZED,
        "유효하지 않은 소셜 토큰입니다.",
        "AUTH401_4"
    ),
    UNSUPPORTED_PROVIDER(
        HttpStatus.BAD_REQUEST,
        "지원하지 않는 소셜 로그인 제공자입니다.",
        "AUTH400_1"
    ),
    SOCIAL_COMMUNICATION_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "소셜 서버와의 통신에 실패했습니다.",
            "AUTH500_1"
    ),
    UNMATCHED_EMAIL_OR_PASSWORD(
        HttpStatus.BAD_REQUEST,
        "이메일이나 패스워드가 틀렸습니다.",
        "AUTH400_2"
    ),
    ;

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
