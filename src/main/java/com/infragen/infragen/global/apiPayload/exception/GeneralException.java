package com.infragen.infragen.global.apiPayload.exception;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {
    private final com.infragen.infragen.global.apiPayload.code.BaseErrorCode code;

    public GeneralException(BaseErrorCode code) {
        super(code.getMessage());
        this.code = code;
    }
}
