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
    PROJECT_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "해당 프로젝트에 대한 접근 권한이 없습니다.",
            "PROJECT403_1"
    ),
    PROJECT_CONCURRENCY_ERROR(
            HttpStatus.CONFLICT,
            "다른 사용자가 동시에 캔버스를 수정하여 저장할 수 없습니다.",
            "PROJECT409_1"
    ),
    PORT_CONFLICT_ERROR(
            HttpStatus.BAD_REQUEST,
            "네트워크 포트가 충돌하여 검증에 실패했습니다.",
            "PROJECT400_2"
    ),
    DEPENDENCY_MISSING_ERROR(
            HttpStatus.BAD_REQUEST,
            "필수 인프라 연결이 누락되었습니다.",
            "PROJECT400_3"
    ),
    DUPLICATE_NODE_NAME(
            HttpStatus.BAD_REQUEST,
            "중복된 노드 이름이 존재합니다.",
            "PROJECT400_4"
    ),
    ;
    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}