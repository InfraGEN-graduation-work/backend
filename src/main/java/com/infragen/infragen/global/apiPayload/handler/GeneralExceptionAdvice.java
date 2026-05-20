package com.infragen.infragen.global.apiPayload.handler;

import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import com.infragen.infragen.global.apiPayload.code.GeneralErrorCode;
import com.infragen.infragen.global.apiPayload.exception.GeneralException;
import com.nimbusds.oauth2.sdk.http.HTTPEndpoint;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GeneralExceptionAdvice {
    // 커스텀 예외 처리
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<@NonNull ApiResponse<Void>> handleException(GeneralException e) {
        BaseErrorCode code = e.getCode();

        if (code.getHttpStatus().is5xxServerError()) {
            log.error("Internal server error:", e);
        } else {
            log.warn("Client error occurred: {}: {}", code.getCode(), code.getMessage());
        }

        return ResponseEntity
                .status(e.getCode().getHttpStatus())
                .body(ApiResponse.onFailure(
                        e.getCode()
                ));
    }

    // @Valid에서 검증 오류가 발생한 예외에 대한 핸들러
    @ExceptionHandler(BindException.class)
    public ResponseEntity<@NonNull ApiResponse<String>> handleValidationException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();

        String message = (fieldError != null) ? fieldError.getDefaultMessage() : "검증 오류가 발생했습니다.";
        String fieldName = (fieldError != null) ? fieldError.getField() : "알 수 없는 필드";

        log.warn("Validation error on field '{}': {}", fieldName, message);

        // 400 Bad Request 로 응답
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure(
                        GeneralErrorCode.BAD_REQUEST,
                        String.format("[%s] %s", fieldName, message)
                ));
    }

    @ExceptionHandler(ParsingException.class)
    public ResponseEntity<@NonNull ApiResponse<String>> handleParsingException(ParsingException e) {
        log.warn("Infrastructure parsing error occurred: {}", e.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure(
                        GeneralErrorCode.BAD_REQUEST,
                        e.getMessage()
                ));

    }
    // 그 외의 정의되지 않은 모든 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<@NonNull ApiResponse<String>> handleException(Exception e) {
        BaseErrorCode code = GeneralErrorCode.INTERNAL_SERVER_ERROR;
        log.error("Internal server error:", e);

        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ApiResponse.onFailure(
                        code,
                        "서버 내부 오류가 발생하였습니다."
                ));
    }
}
