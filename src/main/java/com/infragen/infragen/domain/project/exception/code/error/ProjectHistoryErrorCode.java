package com.infragen.infragen.domain.project.exception.code.error;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ProjectHistoryErrorCode implements BaseErrorCode {
    PROJECT_HISTORY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "프로젝트 히스토리를 찾을 수 없습니다.",
            "PROJECT_HISTORY404_1"
    ),
    ;

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
