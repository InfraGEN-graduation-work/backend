package com.infragen.infragen.domain.parsing.exception;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import com.infragen.infragen.global.apiPayload.exception.GeneralException;

public class ParsingException extends GeneralException {
    public ParsingException(BaseErrorCode errorCode){
        super(errorCode);
    }
}
