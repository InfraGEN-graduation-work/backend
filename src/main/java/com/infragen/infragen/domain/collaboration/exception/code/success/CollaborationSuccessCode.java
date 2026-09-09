package com.infragen.infragen.domain.collaboration.exception.code.success;

import com.infragen.infragen.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CollaborationSuccessCode implements BaseSuccessCode {
    SNAPSHOT_GET_SUCCESS(
            HttpStatus.OK,
            "협업 snapshot 조회에 성공했습니다.",
            "COLLAB200_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
