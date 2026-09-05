package com.infragen.infragen.domain.collaboration.controller;

import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.service.command.CollaborationOperationCommandService;
import com.infragen.infragen.domain.collaboration.dto.request.CollaborationOperationReqDTO;
import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.global.auth.CustomUserDetails;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationOperationMessageControllerTest {
    @Mock
    private CollaborationOperationCommandService operationCommandService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private CollaborationOperationMessageController controller;

    @Test
    void handleOperation_withNewOperation_broadcastsToProjectTopic() {
        // given
        Long projectId = 1L;
        CollaborationOperationReqDTO.Operation operation = operation();
        CollaborationOperationResDTO.BroadcastOperationResDTO result = broadcastResult();
        Principal principal = principal(2L);
        when(operationCommandService.recordOperation(projectId, 2L, operation))
                .thenReturn(Optional.of(result));

        // when
        controller.handleOperation(projectId, principal, operation);

        // then
        verify(messagingTemplate).convertAndSend(
                "/topic/projects/1/operations",
                result
        );
    }

    @Test
    void handleOperation_withDuplicateOperation_doesNotBroadcast() {
        // given
        Long projectId = 1L;
        CollaborationOperationReqDTO.Operation operation = operation();
        Principal principal = principal(2L);
        when(operationCommandService.recordOperation(projectId, 2L, operation))
                .thenReturn(Optional.empty());

        // when
        controller.handleOperation(projectId, principal, operation);

        // then
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handleOperationException_sendsErrorOnlyToRequester() {
        // given
        Principal principal = principal(2L);
        CollaborationException exception = new CollaborationException(
                CollaborationErrorCode.OPERATION_ID_REUSED
        );
        ArgumentCaptor<CollaborationOperationResDTO.OperationErrorResDTO> errorCaptor =
                ArgumentCaptor.forClass(CollaborationOperationResDTO.OperationErrorResDTO.class);

        // when
        controller.handleOperationException(1L, exception, principal);

        // then
        verify(messagingTemplate).convertAndSendToUser(
                eq("2"),
                eq("/queue/projects/1/operation-results"),
                errorCaptor.capture()
        );
        assertEquals("COLLAB409_1", errorCaptor.getValue().code());
    }

    private CollaborationOperationReqDTO.Operation operation() {
        return new CollaborationOperationReqDTO.Operation(
                "op-1",
                "client-1",
                0L,
                CollaborationOperationType.UPDATE_NODE_NAME,
                "node-1",
                Map.of("value", "database")
        );
    }

    private CollaborationOperationResDTO.BroadcastOperationResDTO broadcastResult() {
        return CollaborationOperationResDTO.BroadcastOperationResDTO.builder()
                .operationId("op-1")
                .clientId("client-1")
                .serverVersion(1L)
                .actorMemberId(2L)
                .type(CollaborationOperationType.UPDATE_NODE_NAME)
                .nodeId("node-1")
                .payload(Map.of("value", "database"))
                .build();
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
