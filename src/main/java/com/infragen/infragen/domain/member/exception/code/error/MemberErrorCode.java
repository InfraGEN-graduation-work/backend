package com.infragen.infragen.domain.member.exception.code.error;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MemberErrorCode implements BaseErrorCode {
    MEMBER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "회원 조회에 실패하였습니다.",
            "MEMBER404_1"
    ),
    DUPLICATE_EMAIL(
            HttpStatus.CONFLICT,
            "이미 가입된 이메일입니다.",
            "MEMBER409_1"
    ),
    INVALID_SOCIAL_PROVIDER(
            HttpStatus.BAD_REQUEST,
            "해당 이메일은 다른 소셜 로그인으로 가입되어 있습니다.",
            "MEMBER400_1"
    ),
    ;

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
