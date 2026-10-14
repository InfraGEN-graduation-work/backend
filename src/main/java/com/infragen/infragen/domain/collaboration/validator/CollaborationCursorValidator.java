package com.infragen.infragen.domain.collaboration.validator;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationCursorReqDTO;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import org.springframework.stereotype.Component;

/**
 * 커서 표시·숨김 메시지의 필수 필드와 좌표 범위를 검증한다.
 */
@Component
public class CollaborationCursorValidator {
    // 커서는 노드와 같은 캔버스 좌표계를 쓰므로 ProjectNode.positionX/Y 상한과 맞춘다. 소수점 자리수는 제한하지 않는다.
    private static final double MAX_COORDINATE_ABS = 9_999_999.999;

    /**
     * 커서 메시지가 계약에 맞는지 확인한다.
     *
     * <p>표시 메시지는 좌표 두 개가 모두 필요하다. 숨김 메시지는 좌표가 없어도 되지만, 보냈다면 같은 범위를 지켜야 한다.
     *
     * @param cursor 검증할 커서 메시지
     * @throws CollaborationException 필수 필드가 없거나 좌표가 유한한 범위 밖인 경우
     */
    public void validate(CollaborationCursorReqDTO.Cursor cursor) {
        if (cursor == null || cursor.visible() == null) {
            throw new CollaborationException(CollaborationErrorCode.INVALID_CURSOR);
        }
        if (cursor.visible() && (cursor.x() == null || cursor.y() == null)) {
            throw new CollaborationException(CollaborationErrorCode.INVALID_CURSOR);
        }
        validateCoordinate(cursor.x());
        validateCoordinate(cursor.y());
    }

    private void validateCoordinate(Double coordinate) {
        if (coordinate == null) {
            return;
        }
        if (!Double.isFinite(coordinate) || Math.abs(coordinate) > MAX_COORDINATE_ABS) {
            throw new CollaborationException(CollaborationErrorCode.INVALID_CURSOR);
        }
    }
}
