package com.infragen.infragen.domain.collaboration.exception.code.error;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CollaborationErrorCode implements BaseErrorCode {
    INVALID_OPERATION(
            HttpStatus.BAD_REQUEST,
            "협업 operation 형식이 올바르지 않습니다.",
            "COLLAB400_1"
    ),
    INVALID_OPERATION_PAYLOAD(
            HttpStatus.BAD_REQUEST,
            "협업 operation payload가 올바르지 않습니다.",
            "COLLAB400_2"
    ),
    INVALID_CURSOR(
            HttpStatus.BAD_REQUEST,
            "협업 커서 형식이 올바르지 않습니다.",
            "COLLAB400_3"
    ),
    OPERATION_ID_REUSED(
            HttpStatus.CONFLICT,
            "이미 다른 내용으로 사용된 operationId입니다.",
            "COLLAB409_1"
    ),
    TARGET_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "operation 대상 node를 찾을 수 없습니다.",
            "COLLAB404_1"
    ),
    VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "client의 graph version이 서버와 맞지 않습니다. 최신 snapshot을 다시 조회해야 합니다.",
            "COLLAB409_2"
    ),
    ;

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
