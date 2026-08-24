package com.infragen.infragen.domain.project.exception;

import com.infragen.infragen.global.apiPayload.code.BaseErrorCode;
import com.infragen.infragen.global.apiPayload.exception.GeneralException;

public class ProjectException extends GeneralException {
    public ProjectException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
