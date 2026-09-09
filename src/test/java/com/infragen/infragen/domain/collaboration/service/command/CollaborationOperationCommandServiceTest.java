package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationOperationReqDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.collaboration.validator.CollaborationOperationValidator;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationOperationCommandServiceTest {
    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private CollaborationOperationValidator collaborationOperationValidator;

    @Mock
    private ProjectCollaborationOperationRepository operationRepository;

    @Mock
    private CollaborationOperationTransactionService operationTransactionService;

    @InjectMocks
    private CollaborationOperationCommandService operationCommandService;

    @Test
    @DisplayName("유효한 operation을 transaction service에 위임한다")
    void recordOperation_withValidOperation_delegatesToTransactionService() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        CollaborationOperationReqDTO.Operation operation = operation();
        CollaborationOperationResDTO.BroadcastOperationResDTO broadcast =
                CollaborationOperationResDTO.BroadcastOperationResDTO.builder()
                        .operationId(operation.operationId())
                        .serverVersion(1L)
                        .build();
        when(operationRepository.findByProjectIdAndOperationId(projectId, operation.operationId()))
                .thenReturn(Optional.empty());
        when(operationTransactionService.recordNewOperation(projectId, memberId, operation))
                .thenReturn(Optional.of(broadcast));

        // when
        Optional<CollaborationOperationResDTO.BroadcastOperationResDTO> result =
                operationCommandService.recordOperation(projectId, memberId, operation);

        // then
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().serverVersion());
        verify(operationTransactionService).recordNewOperation(projectId, memberId, operation);
    }

    @Test
    @DisplayName("쓰기 권한이 없으면 project 접근 예외를 발생시킨다")
    void recordOperation_withoutWriteAccess_throwsProjectException() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        CollaborationOperationReqDTO.Operation operation = operation();
        ProjectException accessDenied = new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED);
        doThrow(accessDenied).when(projectAccessService).requireWriteAccess(projectId, memberId);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> operationCommandService.recordOperation(projectId, memberId, operation)
        );

        // then
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, exception.getCode());
        verify(operationTransactionService, never()).recordNewOperation(any(), any(), any());
    }

    @Test
    @DisplayName("잘못된 payload이면 operation 예외를 발생시킨다")
    void recordOperation_withInvalidPayload_throwsCollaborationException() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        CollaborationOperationReqDTO.Operation operation = operation();
        CollaborationException invalidPayload = new CollaborationException(
                CollaborationErrorCode.INVALID_OPERATION_PAYLOAD
        );
        doThrow(invalidPayload).when(collaborationOperationValidator).validate(operation);

        // when
        CollaborationException exception = assertThrows(
                CollaborationException.class,
                () -> operationCommandService.recordOperation(projectId, memberId, operation)
        );

        // then
        assertEquals(CollaborationErrorCode.INVALID_OPERATION_PAYLOAD, exception.getCode());
        verify(operationTransactionService, never()).recordNewOperation(any(), any(), any());
    }

    @Test
    @DisplayName("같은 내용의 operation 재전송은 빈 결과로 처리한다")
    void recordOperation_withExistingSameContent_returnsEmpty() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        CollaborationOperationReqDTO.Operation operation = operation();
        ProjectCollaborationOperation existing = existingOperation(operation, operation.clientId());
        when(operationRepository.findByProjectIdAndOperationId(projectId, operation.operationId()))
                .thenReturn(Optional.of(existing));

        // when
        Optional<?> result = operationCommandService.recordOperation(projectId, memberId, operation);

        // then
        assertFalse(result.isPresent());
        verify(operationTransactionService, never()).recordNewOperation(any(), any(), any());
    }

    @Test
    @DisplayName("같은 operationId를 다른 내용으로 사용하면 충돌을 발생시킨다")
    void recordOperation_withExistingDifferentContent_throwsConflict() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        CollaborationOperationReqDTO.Operation operation = operation();
        ProjectCollaborationOperation existing = existingOperation(operation, "another-client");
        when(operationRepository.findByProjectIdAndOperationId(projectId, operation.operationId()))
                .thenReturn(Optional.of(existing));

        // when
        CollaborationException exception = assertThrows(
                CollaborationException.class,
                () -> operationCommandService.recordOperation(projectId, memberId, operation)
        );

        // then
        assertEquals(CollaborationErrorCode.OPERATION_ID_REUSED, exception.getCode());
    }

    @Test
    @DisplayName("unique 충돌 후 같은 operation이 조회되면 빈 결과를 반환한다")
    void recordOperation_withUniqueConflictAndExistingSameContent_returnsEmpty() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        CollaborationOperationReqDTO.Operation operation = operation();
        ProjectCollaborationOperation existing = existingOperation(operation, operation.clientId());
        when(operationRepository.findByProjectIdAndOperationId(projectId, operation.operationId()))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(operationTransactionService.recordNewOperation(projectId, memberId, operation))
                .thenThrow(new DataIntegrityViolationException("duplicate operationId"));

        // when
        Optional<?> result = operationCommandService.recordOperation(projectId, memberId, operation);

        // then
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("unique 충돌 후 operation을 찾지 못하면 원래 예외를 다시 발생시킨다")
    void recordOperation_withUniqueConflictAndNoExistingOperation_rethrowsException() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        CollaborationOperationReqDTO.Operation operation = operation();
        DataIntegrityViolationException conflict = new DataIntegrityViolationException("duplicate version");
        when(operationRepository.findByProjectIdAndOperationId(projectId, operation.operationId()))
                .thenReturn(Optional.empty());
        when(operationTransactionService.recordNewOperation(projectId, memberId, operation))
                .thenThrow(conflict);

        // when
        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> operationCommandService.recordOperation(projectId, memberId, operation)
        );

        // then
        assertEquals(conflict, exception);
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

    private ProjectCollaborationOperation existingOperation(
            CollaborationOperationReqDTO.Operation operation,
            String clientId
    ) {
        return ProjectCollaborationOperation.builder()
                .operationId(operation.operationId())
                .clientId(clientId)
                .baseVersion(operation.baseVersion())
                .serverVersion(7L)
                .operationType(operation.type())
                .nodeId(operation.nodeId())
                .payload(operation.payload())
                .build();
    }
}
