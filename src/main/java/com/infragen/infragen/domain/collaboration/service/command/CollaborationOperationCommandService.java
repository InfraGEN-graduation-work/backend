package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationOperationReqDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.collaboration.validator.CollaborationOperationValidator;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CollaborationOperationCommandService {
    private final ProjectAccessService projectAccessService;
    private final CollaborationOperationValidator collaborationOperationValidator;
    private final ProjectCollaborationOperationRepository operationRepository;
    private final CollaborationOperationTransactionService operationTransactionService;

    /**
     * operation을 검증하고 transaction service에 저장을 위임한다.
     *
     * @param projectId operation 대상 project 식별자
     * @param memberId operation을 요청한 member 식별자
     * @param operation 저장할 collaboration operation
     * @return 신규 operation 결과 또는 중복 재전송이면 빈 결과
     * @throws CollaborationException 같은 operationId에 다른 내용이 사용된 경우
     */
    public Optional<CollaborationOperationResDTO.BroadcastOperationResDTO> recordOperation(
            Long projectId,
            Long memberId,
            CollaborationOperationReqDTO.Operation operation
    ) {
        validateAndAuthorize(projectId, memberId, operation);

        ProjectCollaborationOperation existing = operationRepository
                .findByProjectIdAndOperationId(projectId, operation.operationId())
                .orElse(null);
        if (existing != null) {
            return resolveExistingOperation(existing, operation);
        }

        try {
            return operationTransactionService.recordNewOperation(projectId, memberId, operation);
        } catch (DataIntegrityViolationException exception) {
            return compensateOperationIdConflict(projectId, operation, exception);
        }
    }

    private Optional<CollaborationOperationResDTO.BroadcastOperationResDTO> resolveExistingOperation(
            ProjectCollaborationOperation existing,
            CollaborationOperationReqDTO.Operation operation
    ) {
        if (!existing.isRetryOf(
                operation.clientId(),
                operation.baseVersion(),
                operation.type(),
                operation.nodeId(),
                operation.payload()
        )) {
            throw new CollaborationException(CollaborationErrorCode.OPERATION_ID_REUSED);
        }
        return Optional.empty();
    }

    // DataIntegrityViolationException이 발생했을 때, 이미 존재하는 operation과 비교하여 동일한 operation인지 확인하고, 동일하다면 빈 결과를 반환하고, 다르다면 예외를 던진다.
    private Optional<CollaborationOperationResDTO.BroadcastOperationResDTO> compensateOperationIdConflict(
            Long projectId,
            CollaborationOperationReqDTO.Operation operation,
            DataIntegrityViolationException exception
    ) {
        ProjectCollaborationOperation existing = operationRepository
                .findByProjectIdAndOperationId(projectId, operation.operationId())
                .orElse(null);
        if (existing == null) {
            throw exception;
        }
        return resolveExistingOperation(existing, operation);
    }

    private void validateAndAuthorize(
            Long projectId,
            Long memberId,
            CollaborationOperationReqDTO.Operation operation
    ) {
        projectAccessService.requireWriteAccess(projectId, memberId);
        collaborationOperationValidator.validate(operation);
    }
}
