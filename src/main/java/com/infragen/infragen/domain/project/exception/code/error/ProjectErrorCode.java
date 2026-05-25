package com.infragen.infragen.domain.project.exception.code.error;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ProjectErrorCode implements BaseErrorCode {
    PROJECT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "프로젝트 조회에 실패하였습니다.",
            "PROJECT404_1"
    ),
    GENERATED_FILE_CANNOT_BE_NULL(
            HttpStatus.BAD_REQUEST,
            "생성된 파일 객체는 null일 수 없습니다.",
            "PROJECT400_1"
    ),
    ;

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
