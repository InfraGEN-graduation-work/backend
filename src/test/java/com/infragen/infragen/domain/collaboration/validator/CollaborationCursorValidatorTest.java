package com.infragen.infragen.domain.collaboration.validator;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationCursorReqDTO;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollaborationCursorValidatorTest {
    private final CollaborationCursorValidator validator = new CollaborationCursorValidator();

    @Test
    @DisplayName("표시 메시지에 유한한 좌표가 있으면 통과한다")
    void validate_visibleWithCoordinates_succeeds() {
        // given
        CollaborationCursorReqDTO.Cursor cursor = new CollaborationCursorReqDTO.Cursor(true, 100.125, -200.5);

        // when
        Executable action = () -> validator.validate(cursor);

        // then
        assertDoesNotThrow(action);
    }

    @Test
    @DisplayName("숨김 메시지는 좌표 없이도 통과한다")
    void validate_hiddenWithoutCoordinates_succeeds() {
        // given
        CollaborationCursorReqDTO.Cursor cursor = new CollaborationCursorReqDTO.Cursor(false, null, null);

        // when
        Executable action = () -> validator.validate(cursor);

        // then
        assertDoesNotThrow(action);
    }

    @Test
    @DisplayName("좌표가 노드 좌표 상한과 같으면 통과한다")
    void validate_coordinateAtLimit_succeeds() {
        // given
        CollaborationCursorReqDTO.Cursor cursor =
                new CollaborationCursorReqDTO.Cursor(true, 9_999_999.999, -9_999_999.999);

        // when
        Executable action = () -> validator.validate(cursor);

        // then
        assertDoesNotThrow(action);
    }

    @Test
    @DisplayName("요청 본문이 없으면 거부한다")
    void validate_nullCursor_throwsInvalidCursor() {
        // given
        CollaborationCursorReqDTO.Cursor cursor = null;

        // when
        CollaborationException exception = assertThrows(CollaborationException.class, () -> validator.validate(cursor));

        // then
        assertEquals(CollaborationErrorCode.INVALID_CURSOR, exception.getCode());
    }

    @Test
    @DisplayName("visible이 없으면 거부한다")
    void validate_missingVisible_throwsInvalidCursor() {
        // given
        CollaborationCursorReqDTO.Cursor cursor = new CollaborationCursorReqDTO.Cursor(null, 1.0, 1.0);

        // when
        CollaborationException exception = assertThrows(CollaborationException.class, () -> validator.validate(cursor));

        // then
        assertEquals(CollaborationErrorCode.INVALID_CURSOR, exception.getCode());
    }

    @Test
    @DisplayName("표시 메시지에 좌표가 하나라도 없으면 거부한다")
    void validate_visibleWithMissingCoordinate_throwsInvalidCursor() {
        // given
        CollaborationCursorReqDTO.Cursor cursor = new CollaborationCursorReqDTO.Cursor(true, 1.0, null);

        // when
        CollaborationException exception = assertThrows(CollaborationException.class, () -> validator.validate(cursor));

        // then
        assertEquals(CollaborationErrorCode.INVALID_CURSOR, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 10_000_000.0, -10_000_000.0})
    @DisplayName("좌표가 유한하지 않거나 상한을 넘으면 거부한다")
    void validate_invalidCoordinate_throwsInvalidCursor(double coordinate) {
        // given
        CollaborationCursorReqDTO.Cursor cursor = new CollaborationCursorReqDTO.Cursor(true, coordinate, 0.0);

        // when
        CollaborationException exception = assertThrows(CollaborationException.class, () -> validator.validate(cursor));

        // then
        assertEquals(CollaborationErrorCode.INVALID_CURSOR, exception.getCode());
    }

    @Test
    @DisplayName("숨김 메시지에 잘못된 좌표가 실려 있으면 거부한다")
    void validate_hiddenWithInvalidCoordinate_throwsInvalidCursor() {
        // given
        CollaborationCursorReqDTO.Cursor cursor = new CollaborationCursorReqDTO.Cursor(false, Double.NaN, null);

        // when
        CollaborationException exception = assertThrows(CollaborationException.class, () -> validator.validate(cursor));

        // then
        assertEquals(CollaborationErrorCode.INVALID_CURSOR, exception.getCode());
    }
}
