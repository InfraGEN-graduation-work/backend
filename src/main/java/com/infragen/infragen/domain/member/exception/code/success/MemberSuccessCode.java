package com.infragen.infragen.domain.member.exception.code.success;

import com.infragen.infragen.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MemberSuccessCode implements BaseSuccessCode {
    MEMBER_GET_SUCCESS(
            HttpStatus.OK,
            "회원 정보 조회에 성공했습니다.",
            "MEMBER200_1"
    ),
    LOGOUT_SUCCESS(
            HttpStatus.OK,
            "로그아웃에 성공했습니다.",
            "MEMBER200_2"
    ),
    ;

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
