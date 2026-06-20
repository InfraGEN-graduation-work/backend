package com.infragen.infragen.domain.parsing.exception.code.error;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ParsingErrorCode implements BaseErrorCode {

    EMPTY_NODES(
            HttpStatus.BAD_REQUEST,
            "node가 없습니다.",
            "PARSING400_1"
    ),
    PROJECT_ID_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "프로젝트 id가 다릅니다.",
            "PARSING400_2"
    ),
    MISSING_COMPONENT_TYPE(
            HttpStatus.BAD_REQUEST,
            "컴포넌트 설정이 누락되었습니다.",
            "PARSING400_3"
    ),
    DUPLICATE_PORT(
            HttpStatus.BAD_REQUEST,
            "중복된 포트 번호가 존재합니다.",
            "PARSING400_4"
    ),
    INVALID_PORT_RANGE(
            HttpStatus.BAD_REQUEST,
            "포트 번호는 1024 ~ 65535 사이여야 합니다.",
            "PARSING400_5"
    ),
    INVALID_DB_NAME(
            HttpStatus.BAD_REQUEST,
            "데이터베이스 이름에는 영문, 숫자, 언더바만 사용할 수 있습니다.",
            "PARSING400_6"
    ),
    INVALID_DB_PASSWORD(
            HttpStatus.BAD_REQUEST,
            "MySQL 루트 비밀번호는 최소 8자리 이상이어야 합니다.",
            "PARSING400_7"
    ),
    INVALID_EDGE_NODE(
            HttpStatus.BAD_REQUEST,
            "존재하지 않는 노드가 연결선에 포함되어 있습니다.",
            "PARSING400_8"
    ),
    CYCLE_DETECTED(
            HttpStatus.BAD_REQUEST,
            "인프라 아키텍처에 순환 참조가 존재합니다.",
            "PARSING400_9"
    ),
    INVALID_COMPONENT_DEPENDENCY(
            HttpStatus.BAD_REQUEST,
            "잘못된 컴포넌트 의존성 방향입니다.",
            "PARSING400_10")
    ,
    UNSUPPORTED_COMPONENT_TYPE(
            HttpStatus.BAD_REQUEST ,
            "지원하지 않는 컴포넌트 타입입니다." ,
            "PARSING400_11")
    ;

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}