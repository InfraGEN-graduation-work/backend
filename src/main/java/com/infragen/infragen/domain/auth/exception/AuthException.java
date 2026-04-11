package com.infragen.infragen.domain.auth.exception;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import com.infragen.infragen.global.apiPayload.exception.GeneralException;

public class AuthException extends GeneralException {
    public AuthException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
