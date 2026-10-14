package com.infragen.infragen.domain.collaboration.event;

import com.infragen.infragen.domain.collaboration.dto.response.CollaborationCursorResDTO;
import com.infragen.infragen.domain.collaboration.service.command.CollaborationCursorSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationCursorSessionListenerTest {
    @Mock
    private CollaborationCursorSessionService cursorSessionService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private CollaborationCursorSessionListener listener;

    @Test
    @DisplayName("연결이 끝나면 보여 주던 project마다 해당 cursors topic으로 숨김을 방송한다")
    void onSessionDisconnected_sendsHiddenToEachProjectTopic() {
        // given
        CollaborationCursorResDTO.BroadcastCursorResDTO hiddenA = hidden("cursor-a");
        CollaborationCursorResDTO.BroadcastCursorResDTO hiddenB = hidden("cursor-a");
        when(cursorSessionService.release("session-a")).thenReturn(Map.of(10L, hiddenA, 20L, hiddenB));

        // when
        listener.onSessionDisconnected(disconnected("session-a"));

        // then
        verify(messagingTemplate).convertAndSend("/topic/projects/10/cursors", hiddenA);
        verify(messagingTemplate).convertAndSend("/topic/projects/20/cursors", hiddenB);
    }

    @Test
    @DisplayName("보여 주던 커서가 없으면 아무것도 방송하지 않는다")
    void onSessionDisconnected_noCursor_sendsNothing() {
        // given
        when(cursorSessionService.release("session-a")).thenReturn(Map.of());

        // when
        listener.onSessionDisconnected(disconnected("session-a"));

        // then
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("한 project 전송이 실패해도 다른 project의 숨김은 계속 보낸다")
    void onSessionDisconnected_sendFailsForOneProject_stillSendsOthers() {
        // given
        CollaborationCursorResDTO.BroadcastCursorResDTO failing = hidden("cursor-a");
        CollaborationCursorResDTO.BroadcastCursorResDTO succeeding = hidden("cursor-a");
        Map<Long, CollaborationCursorResDTO.BroadcastCursorResDTO> released = new LinkedHashMap<>();
        released.put(10L, failing);
        released.put(20L, succeeding);
        when(cursorSessionService.release("session-a")).thenReturn(released);
        doThrow(new MessagingException("test send failure"))
                .when(messagingTemplate).convertAndSend(eq("/topic/projects/10/cursors"), eq(failing));

        // when
        listener.onSessionDisconnected(disconnected("session-a"));

        // then
        verify(messagingTemplate).convertAndSend("/topic/projects/20/cursors", succeeding);
    }

    private CollaborationCursorResDTO.BroadcastCursorResDTO hidden(String cursorId) {
        return CollaborationCursorResDTO.BroadcastCursorResDTO.builder()
                .actorMemberId(2L)
                .cursorId(cursorId)
                .visible(false)
                .build();
    }

    private SessionDisconnectEvent disconnected(String sessionId) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.DISCONNECT);
        headers.setSessionId(sessionId);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, sessionId, CloseStatus.NORMAL);
    }
}
