package com.infragen.infragen.domain.collaboration.exception;

import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.global.apiPayload.exception.GeneralException;

/**
 * collaboration 도메인의 계약 위반과 비즈니스 오류를 표현한다.
 */
public class CollaborationException extends GeneralException {
    /**
     * collaboration 전용 오류 코드로 예외를 생성한다.
     *
     * @param errorCode collaboration 오류 코드
     */
    public CollaborationException(CollaborationErrorCode errorCode) {
        super(errorCode);
    }
}
