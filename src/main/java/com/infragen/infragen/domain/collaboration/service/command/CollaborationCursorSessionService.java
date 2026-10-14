package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.converter.CollaborationCursorConverter;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationCursorResDTO;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 연결별로 커서를 보여 주고 있는 project를 서버 메모리에 기억해, 연결이 끝나면 숨길 대상을 알려 준다.
 *
 * <p>좌표와 시각은 저장하지 않고 단일 인스턴스 메모리만 쓴다. 서버가 종료 이벤트를 받지 못한 연결은 정리하지 못하므로
 * 그 경우는 client 만료에 맡긴다.
 */
@Service
public class CollaborationCursorSessionService {
    private final Map<String, CursorSession> sessions = new ConcurrentHashMap<>();

    /**
     * 검증을 통과한 커서 메시지를 반영한다. 표시면 project를 추가하고, 숨김이면 제거한다.
     *
     * @param sessionId 커서를 보낸 WebSocket session 식별자
     * @param memberId  인증 정보에서 가져온 member 식별자
     * @param cursorId  해당 연결에 부여된 커서 식별자
     * @param projectId 커서를 공유한 project 식별자
     * @param visible   표시 여부
     */
    public void track(String sessionId, Long memberId, String cursorId, Long projectId, boolean visible) {
        sessions.compute(sessionId, (id, current) -> {
            Set<Long> projectIds = current == null ? new HashSet<>() : new HashSet<>(current.projectIds());
            if (visible) {
                projectIds.add(projectId);
            } else {
                projectIds.remove(projectId);
            }
            // 보여 주는 project가 없으면 항목을 지워 메모리에 남기지 않는다.
            return projectIds.isEmpty() ? null : new CursorSession(memberId, cursorId, projectIds);
        });
    }

    /**
     * 연결이 보여 주던 커서를 모두 기억에서 지우고 project별 숨김 메시지를 만든다.
     *
     * <p>종료 이벤트가 같은 session에 여러 번 와도 두 번째부터는 빈 결과를 돌려준다.
     *
     * @param sessionId 종료된 WebSocket session 식별자
     * @return project 식별자별 숨김 메시지. 보여 주던 커서가 없으면 빈 맵
     */
    public Map<Long, CollaborationCursorResDTO.BroadcastCursorResDTO> release(String sessionId) {
        CursorSession session = sessions.remove(sessionId);
        if (session == null) {
            return Map.of();
        }
        Map<Long, CollaborationCursorResDTO.BroadcastCursorResDTO> hidden = new HashMap<>();
        for (Long projectId : session.projectIds()) {
            hidden.put(projectId, CollaborationCursorConverter.toHidden(session.memberId(), session.cursorId()));
        }
        return hidden;
    }

    private record CursorSession(Long memberId, String cursorId, Set<Long> projectIds) {
    }
}
