package com.infragen.infragen.domain.collaboration.controller;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationCursorReqDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationCursorResDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.service.command.CollaborationCursorCommandService;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.global.auth.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationCursorMessageControllerTest {
    @Mock
    private CollaborationCursorCommandService cursorCommandService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private CollaborationCursorMessageController controller;

    @Test
    @DisplayName("검증된 커서는 해당 project의 cursors topic으로만 방송한다")
    void handleCursor_validCursor_broadcastsToProjectCursorTopic() {
        // given
        CollaborationCursorReqDTO.Cursor cursor = new CollaborationCursorReqDTO.Cursor(true, 10.0, 20.0);
        CollaborationCursorResDTO.BroadcastCursorResDTO result = CollaborationCursorResDTO.BroadcastCursorResDTO.builder()
                .actorMemberId(2L)
                .cursorId("cursor-1")
                .visible(true)
                .x(10.0)
                .y(20.0)
                .build();
        when(cursorCommandService.shareCursor(1L, 2L, "session-a", cursor)).thenReturn(result);

        // when
        controller.handleCursor(1L, "session-a", principal(2L), cursor);

        // then
        verify(messagingTemplate).convertAndSend("/topic/projects/1/cursors", result);
    }

    @Test
    @DisplayName("인증 정보에서 가져온 회원 ID를 Service에 넘기고 client가 보낸 값은 쓰지 않는다")
    void handleCursor_usesPrincipalMemberId() {
        // given
        CollaborationCursorReqDTO.Cursor cursor = new CollaborationCursorReqDTO.Cursor(false, null, null);
        when(cursorCommandService.shareCursor(1L, 7L, "session-a", cursor))
                .thenReturn(CollaborationCursorResDTO.BroadcastCursorResDTO.builder().actorMemberId(7L).build());

        // when
        controller.handleCursor(1L, "session-a", principal(7L), cursor);

        // then
        verify(cursorCommandService).shareCursor(1L, 7L, "session-a", cursor);
    }

    @Test
    @DisplayName("인증된 principal이 아니면 Service 호출과 방송 없이 거부한다")
    void handleCursor_unauthenticatedPrincipal_throwsWithoutBroadcast() {
        // given
        Principal anonymous = () -> "anonymous";
        CollaborationCursorReqDTO.Cursor cursor = new CollaborationCursorReqDTO.Cursor(true, 1.0, 1.0);

        // when
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> controller.handleCursor(1L, "session-a", anonymous, cursor)
        );

        // then
        assertEquals("인증된 collaboration principal이 없습니다.", exception.getMessage());
        verifyNoInteractions(cursorCommandService, messagingTemplate);
    }

    @Test
    @DisplayName("커서 오류는 요청자의 operation-results queue에만 전달한다")
    void handleCursorException_sendsErrorOnlyToRequester() {
        // given
        CollaborationException exception = new CollaborationException(CollaborationErrorCode.INVALID_CURSOR);
        ArgumentCaptor<CollaborationOperationResDTO.OperationErrorResDTO> errorCaptor =
                ArgumentCaptor.forClass(CollaborationOperationResDTO.OperationErrorResDTO.class);

        // when
        controller.handleCursorException(1L, exception, principal(2L));

        // then
        verify(messagingTemplate).convertAndSendToUser(
                eq("2"),
                eq("/queue/projects/1/operation-results"),
                errorCaptor.capture()
        );
        assertEquals("COLLAB400_3", errorCaptor.getValue().code());
    }

    private Principal principal(Long memberId) {
        CustomUserDetails userDetails = new CustomUserDetails(
                MemberResDTO.MemberResultDTO.builder()
                        .id(memberId)
                        .isActive(true)
                        .build()
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
    }
}
