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
    MEMBER_UPDATE_SUCCESS(
            HttpStatus.OK,
            "회원 정보 수정이 완료되었습니다.",
            "MEMBER200_3"
    ),
    MEMBER_WITHDRAW_SUCCESS(
            HttpStatus.OK,
            "회원 탈퇴가 완료되었습니다.",
            "MEMBER200_4"
    ),
    ;

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
