package com.infragen.infragen.domain.collaboration.controller;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationCursorReqDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationCursorResDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.collaboration.service.command.CollaborationCursorCommandService;
import com.infragen.infragen.global.apiPayload.exception.GeneralException;
import com.infragen.infragen.global.auth.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * 협업 캔버스의 커서 표시·숨김 메시지를 같은 project room에 전달한다.
 */
@Controller
@RequiredArgsConstructor
public class CollaborationCursorMessageController {
    private final CollaborationCursorCommandService cursorCommandService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 커서 메시지를 검증하고 같은 project 구독자에게 전달한다. 저장하지 않는다.
     *
     * @param projectId 커서를 공유할 project 식별자
     * @param sessionId 커서를 보낸 WebSocket session 식별자
     * @param principal 인증된 STOMP principal
     * @param cursor client가 보낸 커서
     */
    @MessageMapping("/projects/{projectId}/cursors")
    public void handleCursor(
            @DestinationVariable Long projectId,
            @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId,
            Principal principal,
            CollaborationCursorReqDTO.Cursor cursor
    ) {
        CollaborationCursorResDTO.BroadcastCursorResDTO result =
                cursorCommandService.shareCursor(projectId, memberId(principal), sessionId, cursor);
        messagingTemplate.convertAndSend("/topic/projects/" + projectId + "/cursors", result);
    }

    /**
     * 커서 처리 중 발생한 domain 오류를 요청 member에게만 전달한다. 오류 queue는 operation과 같은 경로를 쓴다.
     *
     * @param projectId 오류가 발생한 project 식별자
     * @param exception 전송할 domain 오류
     * @param principal 오류를 요청한 principal
     */
    @MessageExceptionHandler(GeneralException.class)
    public void handleCursorException(
            @DestinationVariable Long projectId,
            GeneralException exception,
            Principal principal
    ) {
        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/projects/" + projectId + "/operation-results",
                CollaborationOperationResDTO.OperationErrorResDTO.builder()
                        .code(exception.getCode().getCode())
                        .message(exception.getCode().getMessage())
                        .build()
        );
    }

    private Long memberId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getMemberId();
        }
        throw new IllegalStateException("인증된 collaboration principal이 없습니다.");
    }
}
