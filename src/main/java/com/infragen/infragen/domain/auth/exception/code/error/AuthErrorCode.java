package com.infragen.infragen.domain.auth.exception.code.error;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;


@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
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
    ;

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
