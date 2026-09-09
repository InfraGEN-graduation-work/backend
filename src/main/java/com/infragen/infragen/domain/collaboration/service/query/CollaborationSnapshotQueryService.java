package com.infragen.infragen.domain.collaboration.service.query;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.collaboration.converter.ProjectCollaborationOperationConverter;
import com.infragen.infragen.domain.collaboration.converter.ProjectCollaborationSnapshotConverter;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationSnapshotResDTO;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationSnapshot;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationState;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationSnapshotRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationStateRepository;
import com.infragen.infragen.domain.project.converter.ProjectConverter;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectEdge;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CollaborationSnapshotQueryService {
    private final ProjectAccessService projectAccessService;
    private final ProjectRepository projectRepository;
    private final ProjectNodeRepository projectNodeRepository;
    private final ProjectEdgeRepository projectEdgeRepository;
    private final ProjectCollaborationOperationRepository operationRepository;
    private final ProjectCollaborationSnapshotRepository snapshotRepository;
    private final ProjectCollaborationStateRepository stateRepository;
    private final ObjectMapper objectMapper;

    /**
     * 초기 graph 또는 요청 version 이후의 operation을 조회한다.
     *
     * @param projectId snapshot을 조회할 project 식별자
     * @param memberId 조회를 요청한 member 식별자
     * @param afterVersion replay 기준 version
     * @return graph 기준 version과 이후 operation 목록
     * @throws CollaborationException afterVersion이 음수인 경우
     * @throws ProjectException project가 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public CollaborationSnapshotResDTO.SnapshotResDTO getSnapshot(
            Long projectId,
            Long memberId,
            Long afterVersion
    ) {
        projectAccessService.requireReadAccess(projectId, memberId);
        if (afterVersion == null || afterVersion < 0) {
            throw new CollaborationException(CollaborationErrorCode.INVALID_OPERATION);
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));
        List<ProjectCollaborationOperation> allOperations =
                operationRepository.findAllByProjectIdOrderByServerVersionAsc(projectId); // serverVersion 기준 오름차순으로 정렬된 operation 목록을 반환한다.

        Long serverVersion = stateRepository.findByProjectId(projectId)
                .map(ProjectCollaborationState::getServerVersion)
                .orElseGet(() -> allOperations.isEmpty()
                        ? 0L
                        : allOperations.get(allOperations.size() - 1).getServerVersion());

        if (afterVersion > serverVersion) {
            throw new CollaborationException(CollaborationErrorCode.VERSION_CONFLICT);
        }

        ProjectCollaborationSnapshot latestSnapshot = snapshotRepository
                .findTopByProjectIdOrderByServerVersionDesc(projectId)
                .filter(snapshot -> snapshot.getServerVersion() <= serverVersion)
                .orElse(null);

        // 해당 조건문의 역할은 다음과 같다.
        // 1. afterVersion이 0인 경우: 클라이언트가 초기 상태에서 동기화를 요청한 경우이므로, 전체 snapshot을 반환한다.
        // 2. afterVersion이 serverVersion보다 작은 경우: 클라이언트가 서버와 동기화되지 않은 상태에서 operation을 전송했을 때 발생할 수 있으므로, operation gap이 발생한 것으로 간주하고 전체 snapshot을 반환한다.
        // 3. afterVersion 이후의 operation이 존재하지 않는 경우: 클라이언트가 서버와 동기화되지 않은 상태에서 operation을 전송했을 때 발생할 수 있으므로, operation gap이 발생한 것으로 간주하고 전체 snapshot을 반환한다.
        if (afterVersion == 0L || hasOperationGap(afterVersion, serverVersion, allOperations)) {
            return buildFullSnapshot(projectId, project, allOperations, serverVersion, latestSnapshot);
        }

        // afterVersion 이후의 operation만 필터링하여 BroadcastOperationResDTO로 변환한다.
        List<CollaborationOperationResDTO.BroadcastOperationResDTO> operations = allOperations.stream()
                .filter(operation -> operation.getServerVersion() > afterVersion)
                .map(ProjectCollaborationOperationConverter::toBroadcast)
                .toList();

        return CollaborationSnapshotResDTO.SnapshotResDTO.builder()
                .graphVersion(afterVersion)
                .serverVersion(serverVersion)
                .operations(operations)
                .build();
    }

    // afterVersion 이후의 operation이 존재하는지 확인한다. afterVersion이 serverVersion보다 작고, afterVersion 이후의 operation이 존재하지 않는 경우에는 operation gap이 발생한 것으로 간주한다.
    private boolean hasOperationGap(
            Long afterVersion,
            Long serverVersion,
            List<ProjectCollaborationOperation> operations
    ) {
        if (operations.isEmpty()) {
            return afterVersion < serverVersion;
        }
        return afterVersion + 1 < operations.get(0).getServerVersion();
    }

    private CollaborationSnapshotResDTO.SnapshotResDTO buildFullSnapshot(
            Long projectId,
            Project project,
            List<ProjectCollaborationOperation> allOperations,
            Long serverVersion,
            ProjectCollaborationSnapshot latestSnapshot
    ) {
        if (latestSnapshot != null) {
            List<CollaborationOperationResDTO.BroadcastOperationResDTO> operations = allOperations.stream()
                    .filter(operation -> operation.getServerVersion() > latestSnapshot.getServerVersion())
                    .map(ProjectCollaborationOperationConverter::toBroadcast)
                    .toList();

            return CollaborationSnapshotResDTO.SnapshotResDTO.builder()
                    .project(ProjectCollaborationSnapshotConverter.toProjectDetailResDTO(
                            latestSnapshot,
                            objectMapper
                    ))
                    .graphVersion(latestSnapshot.getServerVersion())
                    .serverVersion(serverVersion)
                    .operations(operations)
                    .build();
        }

        List<ProjectNode> nodes = projectNodeRepository.findAllByProjectId(projectId);
        List<ProjectEdge> edges = projectEdgeRepository.findAllByProjectId(projectId);
        ProjectResDTO.ProjectDetailResDTO projectDetail = ProjectConverter.toProjectDetailResDTO(
                project,
                nodes,
                edges
        );

        return CollaborationSnapshotResDTO.SnapshotResDTO.builder()
                .project(projectDetail)
                .graphVersion(serverVersion)
                .serverVersion(serverVersion)
                .operations(List.of())
                .build();
    }
}
