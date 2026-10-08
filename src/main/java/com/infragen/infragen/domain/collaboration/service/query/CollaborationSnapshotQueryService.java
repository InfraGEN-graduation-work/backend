package com.infragen.infragen.domain.collaboration.service.query;

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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
     * 초기 graph 또는 요청 version 이후의 operation을 조회한다. client보다 최신 snapshot이 있으면 해당 graph와 그 이후 operation을 반환한다.
     *
     * <p>state의 serverVersion을 먼저 읽어 응답 기준으로 삼고, 필요한 version 구간의 operation만 조회한다.
     * 이미 최신 version인 client에는 operation을 조회하지 않고 빈 delta를 반환한다.
     *
     * @param projectId    snapshot을 조회할 project 식별자
     * @param memberId     조회를 요청한 member 식별자
     * @param afterVersion replay 기준 version
     * @return graph 기준 version과 이후 operation 목록
     * @throws CollaborationException afterVersion이 음수인 경우
     * @throws ProjectException       project가 존재하지 않는 경우
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

        // 응답 기준 version을 먼저 정하고, 이후 모든 operation 조회의 상한으로 쓴다.
        Long serverVersion = stateRepository.findByProjectId(projectId)
                .map(ProjectCollaborationState::getServerVersion)
                .orElseGet(() -> operationRepository.findFirstByProjectIdOrderByServerVersionDesc(projectId)
                        .map(ProjectCollaborationOperation::getServerVersion)
                        .orElse(0L));

        if (afterVersion > serverVersion) {
            throw new CollaborationException(CollaborationErrorCode.VERSION_CONFLICT);
        }

        // 이미 최신 version을 가진 client에는 replay 조회 없이 빈 delta를 반환한다. 0은 초기 graph 요청이다.
        if (afterVersion > 0L && afterVersion.equals(serverVersion)) {
            return buildDelta(afterVersion, serverVersion, List.of());
        }

        ProjectCollaborationSnapshot latestSnapshot = snapshotRepository
                .findTopByProjectIdOrderByServerVersionDesc(projectId)
                .filter(snapshot -> snapshot.getServerVersion() <= serverVersion)
                .orElse(null);

        // PUT version은 operation log에 없으므로 최신 snapshot 이전 client에는 graph도 전달한다.
        if (afterVersion == 0L
                || (latestSnapshot != null && afterVersion < latestSnapshot.getServerVersion())) {
            return buildFullSnapshot(projectId, project, serverVersion, latestSnapshot);
        }

        List<ProjectCollaborationOperation> operations =
                operationRepository.findAllInVersionRange(projectId, afterVersion, serverVersion);
        if (hasOperationGap(projectId, afterVersion, serverVersion, operations)) {
            return buildFullSnapshot(projectId, project, serverVersion, latestSnapshot);
        }

        return buildDelta(afterVersion, serverVersion, operations);
    }

    private CollaborationSnapshotResDTO.SnapshotResDTO buildDelta(
            Long afterVersion,
            Long serverVersion,
            List<ProjectCollaborationOperation> operations
    ) {
        return CollaborationSnapshotResDTO.SnapshotResDTO.builder()
                .graphVersion(afterVersion)
                .serverVersion(serverVersion)
                .operations(toBroadcasts(operations))
                .build();
    }

    // 구간의 첫 log가 afterVersion + 1이면 이어지는 것이므로 gap이 아니다. 그렇지 않을 때만 project 전체의 첫 log를 확인한다.
    // 전체 log가 없으면 afterVersion이 serverVersion보다 작을 때, 있으면 첫 log가 afterVersion + 1보다 클 때 gap으로 본다.
    private boolean hasOperationGap(
            Long projectId,
            Long afterVersion,
            Long serverVersion,
            List<ProjectCollaborationOperation> operations
    ) {
        if (!operations.isEmpty() && operations.getFirst().getServerVersion() == afterVersion + 1) {
            return false;
        }
        return operationRepository.findFirstByProjectIdOrderByServerVersionAsc(projectId)
                .map(first -> afterVersion + 1 < first.getServerVersion())
                .orElse(afterVersion < serverVersion);
    }

    private List<CollaborationOperationResDTO.BroadcastOperationResDTO> toBroadcasts(
            List<ProjectCollaborationOperation> operations
    ) {
        return operations.stream()
                .map(ProjectCollaborationOperationConverter::toBroadcast)
                .toList();
    }

    private CollaborationSnapshotResDTO.SnapshotResDTO buildFullSnapshot(
            Long projectId,
            Project project,
            Long serverVersion,
            ProjectCollaborationSnapshot latestSnapshot
    ) {
        if (latestSnapshot != null) {
            // snapshot에 이미 반영된 version은 제외하고 serverVersion까지의 log만 replay한다.
            List<CollaborationOperationResDTO.BroadcastOperationResDTO> operations = toBroadcasts(
                    operationRepository.findAllInVersionRange(
                            projectId,
                            latestSnapshot.getServerVersion(),
                            serverVersion
                    )
            );

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
