package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.converter.CollaborationCursorConverter;
import com.infragen.infragen.domain.collaboration.dto.request.CollaborationCursorReqDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationCursorResDTO;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.validator.CollaborationCursorValidator;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 커서 메시지를 권한과 형식으로 검증해 방송할 응답으로 만든다. 커서는 저장하지 않고 operation log, version, snapshot도 건드리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CollaborationCursorCommandService {
    private final ProjectAccessService projectAccessService;
    private final CollaborationCursorValidator cursorValidator;

    /**
     * 읽기 권한과 좌표를 확인하고 서버가 식별한 회원·연결 정보를 붙여 방송용 커서를 만든다.
     *
     * <p>VIEWER도 커서를 보낼 수 있다. 그래프를 수정하지 않기 때문이다.
     *
     * @param projectId 커서를 공유할 project 식별자
     * @param memberId 인증 정보에서 가져온 member 식별자
     * @param sessionId 커서를 보낸 WebSocket session 식별자
     * @param cursor client가 보낸 커서
     * @return 같은 project 참여자에게 방송할 커서
     * @throws CollaborationException 커서 형식이 올바르지 않은 경우
     */
    public CollaborationCursorResDTO.BroadcastCursorResDTO shareCursor(
            Long projectId,
            Long memberId,
            String sessionId,
            CollaborationCursorReqDTO.Cursor cursor
    ) {
        projectAccessService.requireReadAccess(projectId, memberId);
        cursorValidator.validate(cursor);
        return CollaborationCursorConverter.toBroadcast(cursor, memberId, cursorId(sessionId));
    }

    // session ID를 그대로 노출하지 않는다. 같은 연결은 항상 같은 값을 받으므로 서버에 매핑을 저장하지 않는다.
    private String cursorId(String sessionId) {
        return UUID.nameUUIDFromBytes(sessionId.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
