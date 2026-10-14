package com.infragen.infragen.domain.collaboration.dto.request;

public final class CollaborationCursorReqDTO {
    private CollaborationCursorReqDTO() {
    }

    /**
     * 커서 표시·숨김 요청. 숨김일 때는 좌표를 보내지 않아도 된다.
     */
    public record Cursor(
            Boolean visible,
            Double x,
            Double y
    ) {
    }
}
