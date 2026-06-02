package com.infragen.infragen.domain.project.exception.code.success;

import com.infragen.infragen.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ProjectHistorySuccessCode implements BaseSuccessCode {
    HISTORY_CREATE_SUCCESS(
            HttpStatus.CREATED,
            "프로젝트 히스토리 생성에 성공했습니다.",
            "PROJECT_HISTORY201_1"
    ),
    HISTORY_GET_SUCCESS(
            HttpStatus.OK,
            "프로젝트 히스토리 목록 조회에 성공했습니다.",
            "PROJECT_HISTORY200_1"
    ),
    HISTORY_DETAIL_GET_SUCCESS(
            HttpStatus.OK,
            "프로젝트 히스토리 상세 조회에 성공했습니다.",
            "PROJECT_HISTORY200_2"
    ),
    ;

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
