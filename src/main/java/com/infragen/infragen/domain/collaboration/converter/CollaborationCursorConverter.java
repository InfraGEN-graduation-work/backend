package com.infragen.infragen.domain.collaboration.converter;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationCursorReqDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationCursorResDTO;

public final class CollaborationCursorConverter {
    private CollaborationCursorConverter() {
    }

    /**
     * 검증된 커서 요청에 서버가 확인한 회원과 연결 식별자를 붙여 방송용 응답으로 변환한다.
     *
     * @param cursor 검증을 마친 커서 요청
     * @param actorMemberId 인증 정보에서 가져온 회원 식별자
     * @param cursorId 서버가 연결마다 부여한 커서 식별자
     * @return 같은 프로젝트 참여자에게 보낼 커서 응답
     */
    public static CollaborationCursorResDTO.BroadcastCursorResDTO toBroadcast(
            CollaborationCursorReqDTO.Cursor cursor,
            Long actorMemberId,
            String cursorId
    ) {
        return CollaborationCursorResDTO.BroadcastCursorResDTO.builder()
                .actorMemberId(actorMemberId)
                .cursorId(cursorId)
                .visible(cursor.visible())
                .x(cursor.x())
                .y(cursor.y())
                .build();
    }
}
