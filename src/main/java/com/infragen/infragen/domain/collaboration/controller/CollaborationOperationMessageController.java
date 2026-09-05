package com.infragen.infragen.domain.collaboration.controller;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationOperationReqDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.collaboration.service.command.CollaborationOperationCommandService;
import com.infragen.infragen.global.apiPayload.exception.GeneralException;
import com.infragen.infragen.global.auth.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * collaboration operation message를 검증·저장하고 승인된 변경을 room에 broadcast한다.
 */
@Controller
@RequiredArgsConstructor
public class CollaborationOperationMessageController {
    private final CollaborationOperationCommandService operationCommandService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * project operation을 처리하고 신규 operation만 같은 room에 전달한다.
     *
     * @param projectId operation 대상 project 식별자
     * @param principal 인증된 STOMP principal
     * @param operation client가 보낸 operation
     */
    @MessageMapping("/projects/{projectId}/operations")
    public void handleOperation(
            @DestinationVariable Long projectId,
            Principal principal,
            CollaborationOperationReqDTO.Operation operation
    ) {
        Long memberId = memberId(principal);
        operationCommandService.recordOperation(projectId, memberId, operation)
                .ifPresent(result -> messagingTemplate.convertAndSend(
                        "/topic/projects/" + projectId + "/operations",
                        result
                ));
    }

    /**
     * operation 처리 중 발생한 domain 오류를 요청 member에게만 전달한다.
     *
     * @param projectId 오류가 발생한 project 식별자
     * @param exception 전송할 domain 오류
     * @param principal 오류를 요청한 principal
     */
    @MessageExceptionHandler(GeneralException.class)
    public void handleOperationException(
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
