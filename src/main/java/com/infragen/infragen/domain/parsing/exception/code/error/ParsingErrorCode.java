package com.infragen.infragen.domain.parsing.exception.code.error;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ParsingErrorCode implements BaseErrorCode {

    INVALID_NGINX_PROPERTIES(HttpStatus.BAD_REQUEST,
            "NGINX imageVersion 또는 containerName이 잘못되었습니다. 단일 앱 프록시만 지원합니다.", "PARSING400_26"),
    INVALID_NGINX_CONNECTION(HttpStatus.BAD_REQUEST,
            "NGINX는 단일 앱 그래프에서 APPLICATION → NGINX로 하나만 연결해야 합니다.", "PARSING400_27"),
    INVALID_NGINX_PORT(HttpStatus.BAD_REQUEST,
            "NGINX 포트는 정수 1 ~ 65535여야 합니다.", "PARSING400_28"),
    EMPTY_NODES(
            HttpStatus.BAD_REQUEST,
            "node가 없습니다.",
            "PARSING400_1"
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
            "데이터베이스 비밀번호는 최소 8자리 이상이어야 합니다.",
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
            "PARSING400_10"
    ),
    UNSUPPORTED_COMPONENT_TYPE(
            HttpStatus.BAD_REQUEST,
            "지원하지 않는 컴포넌트 타입입니다.",
            "PARSING400_11"
    ),
    MISSING_SPRING_BOOT_NAME(
            HttpStatus.BAD_REQUEST,
            "Spring Boot 서비스 이름이 누락되었습니다.",
            "PARSING400_12"
    ),
    MISSING_JAVA_VERSION(
            HttpStatus.BAD_REQUEST,
            "Java 버전이 누락되었습니다.",
            "PARSING400_13"
    ),
    MISSING_NODE_ID(
            HttpStatus.BAD_REQUEST,
            "nodeId가 누락되었습니다.",
            "PARSING400_14"
    ),
    DUPLICATE_NODE_ID(
            HttpStatus.BAD_REQUEST,
            "중복된 nodeId가 존재합니다.",
            "PARSING400_15"
    ),
    INVALID_EDGE_ENDPOINT(
            HttpStatus.BAD_REQUEST,
            "연결선의 source 또는 target이 누락되었습니다.",
            "PARSING400_16"
    ),
    MISSING_MYSQL_IMAGE_VERSION(
            HttpStatus.BAD_REQUEST,
            "MySQL 이미지 버전이 누락되었습니다.",
            "PARSING400_17"
    ),
    MISSING_MYSQL_USERNAME(
            HttpStatus.BAD_REQUEST,
            "MySQL 사용자 이름이 누락되었습니다.",
            "PARSING400_18"
    ),
    MISSING_MYSQL_USER_PASSWORD(
            HttpStatus.BAD_REQUEST,
            "MySQL 사용자 비밀번호가 누락되었습니다.",
            "PARSING400_19"
    ),
    MISSING_REDIS_IMAGE_VERSION(
            HttpStatus.BAD_REQUEST,
            "Redis 이미지 버전이 누락되었습니다.",
            "PARSING400_20"
    ),
    MISSING_REDIS_PASSWORD(
            HttpStatus.BAD_REQUEST,
            "Redis password가 누락되었습니다.",
            "PARSING400_22"
    ),
    INVALID_JAVA_VERSION(
            HttpStatus.BAD_REQUEST,
            "Java 버전은 숫자 형식이어야 합니다.",
            "PARSING400_21"
    ),
    MISSING_POSTGRES_IMAGE_VERSION(
            HttpStatus.BAD_REQUEST,
            "PostgreSQL 이미지 버전이 누락되었습니다.",
            "PARSING400_23"
    ),
    MISSING_POSTGRES_USERNAME(
            HttpStatus.BAD_REQUEST,
            "PostgreSQL 사용자 이름이 누락되었습니다.",
            "PARSING400_24"
    ),
    DUPLICATE_DEPENDENCY_TYPE(
            HttpStatus.BAD_REQUEST,
            "하나의 애플리케이션에 같은 타입의 의존 컴포넌트를 둘 이상 연결할 수 없습니다.",
            "PARSING400_25"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
