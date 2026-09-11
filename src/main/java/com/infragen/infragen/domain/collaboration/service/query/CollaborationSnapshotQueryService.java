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
     * client보다 최신 snapshot이 있으면 해당 graph와 그 이후 operation을 반환한다.
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

        // PUT version은 operation log에 없으므로 최신 snapshot 이전 client에는 graph도 전달한다.
        if (afterVersion == 0L
                || (latestSnapshot != null && afterVersion < latestSnapshot.getServerVersion())
                || hasOperationGap(afterVersion, serverVersion, allOperations)) {
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
