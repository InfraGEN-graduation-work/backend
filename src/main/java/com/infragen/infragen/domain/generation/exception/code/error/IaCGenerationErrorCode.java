package com.infragen.infragen.domain.generation.exception.code.error;

import org.springframework.http.HttpStatus;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum IaCGenerationErrorCode implements BaseErrorCode {

    UNSUPPORTED_OUTPUT_FORMAT(
        HttpStatus.BAD_REQUEST,
        "지원하지 않는 출력 형식입니다.",
        "GENERATION400_1"
    ),
    INVALID_COMPONENT_STATE(
        HttpStatus.BAD_REQUEST,
        "인프라 코드 생성에 필요한 컴포넌트 상태가 올바르지 않습니다.",
        "GENERATION400_2"
    ),
    INVALID_GENERATION_REQUEST(
        HttpStatus.BAD_REQUEST,
        "인프라 코드 생성 요청이 올바르지 않습니다.",
        "GENERATION400_3"
    ),
    MISSING_DEPLOYMENT_OPTION(
        HttpStatus.BAD_REQUEST,
        "배포 옵션이 누락되었습니다.",
        "GENERATION400_4"
    ),
    MISSING_DEPLOYMENT_TARGET(
        HttpStatus.BAD_REQUEST,
        "Cloud 배포 대상 설정이 누락되었습니다.",
        "GENERATION400_5"
    ),
    INVALID_LOCAL_DEPLOYMENT_CONFIGURATION(
        HttpStatus.BAD_REQUEST,
        "Local 배포 설정 조합이 올바르지 않습니다.",
        "GENERATION400_6"
    ),
    DEPLOYMENT_TARGET_PROVIDER_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "배포 옵션과 배포 대상 provider가 일치하지 않습니다.",
        "GENERATION400_7"
    ),
    INVALID_DEPLOYMENT_TARGET(
        HttpStatus.BAD_REQUEST,
        "Cloud 배포 대상 설정이 올바르지 않습니다.",
        "GENERATION400_8"
    ),
    AMBIGUOUS_DEPENDENCY_CONFIGURATION(
        HttpStatus.BAD_REQUEST,
        "동일한 유형의 Cloud dependency 설정이 중복되었습니다.",
        "GENERATION400_9"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
