package com.infragen.infragen.domain.member.enums;

import java.util.Locale;

import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;

public enum SocialProvider {
    KAKAO, NAVER, GOOGLE;

    public static SocialProvider fromString(String providerName) {
        if (providerName == null) {
            throw new AuthException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        }

        try {
            return valueOf(providerName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AuthException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        }
    }
}
