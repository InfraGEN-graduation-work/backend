package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.converter.ProjectCollaborationOperationConverter;
import com.infragen.infragen.domain.collaboration.converter.ProjectCollaborationSnapshotConverter;
import com.infragen.infragen.domain.collaboration.dto.request.CollaborationOperationReqDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.event.ProjectCollaborationCheckpointEvent;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.converter.ProjectConverter;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CollaborationOperationTransactionService {
    private static final long SNAPSHOT_INTERVAL = 50L;

    private final ProjectCollaborationVersionService projectCollaborationVersionService;
    private final ProjectCollaborationOperationRepository operationRepository;
    private final ProjectRepository projectRepository;
    private final ProjectNodeRepository projectNodeRepository;
    private final ProjectEdgeRepository projectEdgeRepository;
    private final MemberQueryService memberQueryService;
    private final ProjectAccessService projectAccessService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * 검증된 신규 operation을 materialize하고 operation log에 저장한다.
     *
     * @param projectId operation 대상 project 식별자
     * @param memberId operation을 요청한 member 식별자
     * @param operation 저장할 collaboration operation
     * @return 신규 operation 결과 또는 lock 안에서 발견한 재전송 결과
     * @throws CollaborationException operationId가 다른 내용으로 재사용된 경우
     * @throws ProjectException project 또는 대상 node가 존재하지 않거나, lock을 얻은 시점에 member의 쓰기 권한이 없는 경우
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<CollaborationOperationResDTO.BroadcastOperationResDTO> recordNewOperation(
            Long projectId,
            Long memberId,
            CollaborationOperationReqDTO.Operation operation
    ) {
        ProjectCollaborationVersionService.VersionIssuance issuance =
                projectCollaborationVersionService.issueNextVersion(
                        projectId,
                        operation.baseVersion(),
                        operation.operationId()
                );
        // 호출 전 권한 확인과 이 lock 사이에 권한이 회수되고 커밋됐을 수 있어 lock 뒤에 한 번 더 확인한다.
        // 재전송 분기보다 앞에 둬서 권한을 잃은 member의 재전송도 빈 결과가 아니라 거부로 응답한다.
        projectAccessService.requireWriteAccess(projectId, memberId);

        ProjectCollaborationOperation existing = issuance.existingOperation();
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
        applyOperation(targetNode, operation);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));
        Long serverVersion = issuance.serverVersion();

        ProjectCollaborationOperation savedOperation = operationRepository.save(
                ProjectCollaborationOperationConverter.toEntity(
                        operation,
                        project,
                        memberQueryService.findById(memberId),
                        serverVersion
                )
        );

        publishCheckpointEventIfNeeded(projectId, memberId, serverVersion, project);
        return Optional.of(ProjectCollaborationOperationConverter.toBroadcast(
                operation,
                savedOperation.getServerVersion(),
                memberId
        ));
    }

    private void publishCheckpointEventIfNeeded(
            Long projectId,
            Long memberId,
            Long serverVersion,
            Project project
    ) {
        if (serverVersion % SNAPSHOT_INTERVAL != 0) {
            return;
        }

        ProjectResDTO.ProjectDetailResDTO projectDetail = ProjectConverter.toProjectDetailResDTO(
                project,
                projectNodeRepository.findAllByProjectId(projectId),
                projectEdgeRepository.findAllByProjectId(projectId)
        );
        Map<String, Object> graphPayload = ProjectCollaborationSnapshotConverter.toGraphPayload(
                projectDetail,
                objectMapper
        );
        eventPublisher.publishEvent(new ProjectCollaborationCheckpointEvent(
                projectId,
                memberId,
                serverVersion,
                graphPayload
        ));
    }

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
}
