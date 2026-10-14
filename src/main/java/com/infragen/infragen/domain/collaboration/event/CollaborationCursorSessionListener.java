package com.infragen.infragen.domain.collaboration.event;

import com.infragen.infragen.domain.collaboration.service.command.CollaborationCursorSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * STOMP 연결이 끝나면 그 연결이 보여 주던 커서를 같은 project room에서 숨긴다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CollaborationCursorSessionListener {
    private final CollaborationCursorSessionService cursorSessionService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 종료된 연결의 커서를 project마다 숨김 메시지로 방송한다. 같은 이벤트가 중복돼도 한 번만 방송한다.
     */
    @EventListener
    public void onSessionDisconnected(SessionDisconnectEvent event) {
        cursorSessionService.release(event.getSessionId()).forEach((projectId, hidden) -> {
            // 정리 대상은 이미 기억에서 지워졌으므로, 한 project의 전송 실패가 다른 project의 숨김을 막지 않게 한다.
            try {
                messagingTemplate.convertAndSend("/topic/projects/" + projectId + "/cursors", hidden);
            } catch (MessagingException exception) {
                log.warn("연결 종료 커서 숨김 전송 실패: projectId={}, sessionId={}", projectId, event.getSessionId(), exception);
            }
        });
    }
}
