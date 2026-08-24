package com.infragen.infragen.domain.generation.exception;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import com.infragen.infragen.global.apiPayload.exception.GeneralException;

public class IaCGenerationException extends GeneralException {
    public IaCGenerationException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
