package com.infragen.infragen.domain.project.exception.code.success;

import com.infragen.infragen.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
@Getter
@AllArgsConstructor
public enum ProjectSuccessCode implements BaseSuccessCode {
    PROJECT_CREATE_SUCCESS(
        HttpStatus.CREATED,
        "프로젝트 생성에 성공했습니다.",
        "PROJECT201_1"
    ),
    PROJECT_GET_SUCCESS(
        HttpStatus.OK,
        "프로젝트 목록 조회에 성공했습니다.",
        "PROJECT200_1"
    ),
    PROJECT_CANVAS_GET_SUCCESS(
        HttpStatus.OK,
        "프로젝트 캔버스 조회에 성공했습니다.",
        "PROJECT200_2"
    ),
    PROJECT_CANVAS_SAVE_SUCCESS(
        HttpStatus.OK,
        "프로젝트 캔버스 저장에 성공했습니다.",
        "PROJECT200_3"
    ),
    PROJECT_DELETE_SUCCESS(
        HttpStatus.OK,
        "프로젝트 삭제에 성공했습니다.",
        "PROJECT200_4"
    ),
    ;
    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}