package com.infragen.infragen.domain.collaboration.service.query;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.collaboration.converter.ProjectCollaborationOperationConverter;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationSnapshotResDTO;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
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

@Service
@RequiredArgsConstructor
public class CollaborationSnapshotQueryService {
    private final ProjectAccessService projectAccessService;
    private final ProjectRepository projectRepository;
    private final ProjectNodeRepository projectNodeRepository;
    private final ProjectEdgeRepository projectEdgeRepository;
    private final ProjectCollaborationOperationRepository operationRepository;

    /**
     * project의 materialized graph와 기준 version 이후 operation을 함께 조회한다.
     *
     * @param projectId snapshot을 조회할 project 식별자
     * @param memberId 조회를 요청한 member 식별자
     * @param afterVersion replay 기준 version
     * @return graph snapshot과 이후 operation 목록
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
        List<ProjectNode> nodes = projectNodeRepository.findAllByProjectId(projectId);
        List<ProjectEdge> edges = projectEdgeRepository.findAllByProjectId(projectId);
        ProjectResDTO.ProjectDetailResDTO projectDetail = ProjectConverter.toProjectDetailResDTO(
                project,
                nodes,
                edges
        );

        List<ProjectCollaborationOperation> allOperations =
                operationRepository.findAllByProjectIdOrderByServerVersionAsc(projectId); // serverVersion 기준 오름차순으로 정렬된 operation 목록을 반환한다.
        List<CollaborationOperationResDTO.BroadcastOperationResDTO> operations = allOperations.stream()
                .filter(operation -> operation.getServerVersion() > afterVersion)
                .map(ProjectCollaborationOperationConverter::toBroadcast)
                .toList();
        Long serverVersion = allOperations.isEmpty()
                ? 0L
                : allOperations.get(allOperations.size() - 1).getServerVersion();

        return CollaborationSnapshotResDTO.SnapshotResDTO.builder()
                .project(projectDetail)
                .serverVersion(serverVersion)
                .operations(operations)
                .build();
    }
}
