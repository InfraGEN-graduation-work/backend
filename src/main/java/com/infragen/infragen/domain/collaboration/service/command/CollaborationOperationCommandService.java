package com.infragen.infragen.domain.collaboration.service.command;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.collaboration.converter.ProjectCollaborationOperationConverter;
import com.infragen.infragen.domain.collaboration.dto.request.CollaborationOperationReqDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.collaboration.validator.CollaborationOperationValidator;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;

import lombok.RequiredArgsConstructor;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CollaborationOperationCommandService {
    private final ProjectAccessService projectAccessService;
    private final CollaborationOperationValidator collaborationOperationValidator;
    private final ProjectCollaborationVersionService projectCollaborationVersionService;
    private final ProjectCollaborationOperationRepository operationRepository;
    private final ProjectRepository projectRepository;
    private final ProjectNodeRepository projectNodeRepository;
    private final MemberQueryService memberQueryService;

    /**
     * 검증된 operation을 project log에 한 번만 저장하고 serverVersion을 반환한다.
     *
     * @param projectId operation 대상 project 식별자
     * @param memberId operation을 요청한 member 식별자
     * @param operation 저장할 collaboration operation
     * @return 신규 또는 기존 operation의 serverVersion
     * @throws CollaborationException 같은 operationId에 다른 내용이 사용된 경우
     */
    @Transactional
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

        ProjectNode targetNode = projectNodeRepository.findByProjectIdAndNodeId(
                        projectId,
                        operation.nodeId()
                ).orElseThrow(() -> new CollaborationException(CollaborationErrorCode.TARGET_NOT_FOUND));

        Long serverVersion = projectCollaborationVersionService.issueNextVersion(
                projectId,
                operation.baseVersion()
        );
        
        applyOperation(targetNode, operation);
        
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));

        ProjectCollaborationOperation savedOperation = operationRepository.save(
                ProjectCollaborationOperationConverter.toEntity(
                        operation,
                        project,
                        memberQueryService.findById(memberId),
                        serverVersion
                )
        );
        return Optional.of(ProjectCollaborationOperationConverter.toBroadcast(
                operation,
                savedOperation.getServerVersion(),
                memberId
        ));
    }

    // operation의 type에 따라 targetNode에 대한 변경을 수행한다.
    private void applyOperation(
            ProjectNode targetNode,
            CollaborationOperationReqDTO.Operation operation
    ) {
        switch (operation.type()) {
            case UPDATE_NODE_NAME -> targetNode.renameTo((String) operation.payload().get("value"));
            case UPDATE_NODE_POSITION -> targetNode.moveTo(
                    new BigDecimal(operation.payload().get("positionX").toString()),
                    new BigDecimal(operation.payload().get("positionY").toString())
            );
        }
    }

    // 프로젝트에 대한 쓰기 권한을 확인하고, operation의 유효성을 검증한다.
    private void validateAndAuthorize(
            Long projectId,
            Long memberId,
            CollaborationOperationReqDTO.Operation operation
    ) {
        projectAccessService.requireWriteAccess(projectId, memberId);
        collaborationOperationValidator.validate(operation);
    }
}
