package com.infragen.infragen.domain.parsing.exception.code.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParsingErrorCodeTest {

    @Test
    @DisplayName("서로 다른 파싱 오류는 고유한 응답 코드를 가진다")
    void getCode_DifferentErrors_ReturnsUniqueCodes() {
        // given
        ParsingErrorCode[] errorCodes = ParsingErrorCode.values();

        // when
        long uniqueCodeCount = Arrays.stream(errorCodes)
                .map(ParsingErrorCode::getCode)
                .distinct()
                .count();

        // then
        assertEquals(errorCodes.length, uniqueCodeCount);
    }
}
