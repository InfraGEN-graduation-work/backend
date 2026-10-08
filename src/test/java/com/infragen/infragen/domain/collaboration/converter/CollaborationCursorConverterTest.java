package com.infragen.infragen.domain.collaboration.converter;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationCursorReqDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationCursorResDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CollaborationCursorConverterTest {
    @Test
    @DisplayName("요청의 표시 상태와 좌표에 서버가 확인한 회원·커서 식별자를 붙인다")
    void toBroadcast_visibleCursor_mapsAllFields() {
        // given
        CollaborationCursorReqDTO.Cursor cursor = new CollaborationCursorReqDTO.Cursor(true, 10.5, -20.25);

        // when
        CollaborationCursorResDTO.BroadcastCursorResDTO result =
                CollaborationCursorConverter.toBroadcast(cursor, 2L, "cursor-1");

        // then
        assertAll(
                () -> assertEquals(2L, result.actorMemberId()),
                () -> assertEquals("cursor-1", result.cursorId()),
                () -> assertEquals(true, result.visible()),
                () -> assertEquals(10.5, result.x()),
                () -> assertEquals(-20.25, result.y())
        );
    }

    @Test
    @DisplayName("숨김 요청의 좌표 없음은 그대로 null로 전달한다")
    void toBroadcast_hiddenCursor_keepsNullCoordinates() {
        // given
        CollaborationCursorReqDTO.Cursor cursor = new CollaborationCursorReqDTO.Cursor(false, null, null);

        // when
        CollaborationCursorResDTO.BroadcastCursorResDTO result =
                CollaborationCursorConverter.toBroadcast(cursor, 2L, "cursor-1");

        // then
        assertAll(
                () -> assertEquals(false, result.visible()),
                () -> assertNull(result.x()),
                () -> assertNull(result.y())
        );
    }

    @Test
    @DisplayName("연결 종료 숨김 응답은 회원·커서 식별자만 담고 좌표는 없다")
    void toHidden_returnsHiddenWithoutCoordinates() {
        // given
        Long actorMemberId = 2L;

        // when
        CollaborationCursorResDTO.BroadcastCursorResDTO result =
                CollaborationCursorConverter.toHidden(actorMemberId, "cursor-1");

        // then
        assertAll(
                () -> assertEquals(2L, result.actorMemberId()),
                () -> assertEquals("cursor-1", result.cursorId()),
                () -> assertEquals(false, result.visible()),
                () -> assertNull(result.x()),
                () -> assertNull(result.y())
        );
    }
}
