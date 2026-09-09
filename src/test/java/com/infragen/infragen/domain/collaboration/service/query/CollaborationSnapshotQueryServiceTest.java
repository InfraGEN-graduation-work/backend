package com.infragen.infragen.domain.collaboration.service.query;

import com.infragen.infragen.domain.collaboration.dto.response.CollaborationSnapshotResDTO;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationSnapshot;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationState;
import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationSnapshotRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationStateRepository;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationSnapshotQueryServiceTest {
    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectNodeRepository projectNodeRepository;

    @Mock
    private ProjectEdgeRepository projectEdgeRepository;

    @Mock
    private ProjectCollaborationOperationRepository operationRepository;

    @Mock
    private ProjectCollaborationSnapshotRepository snapshotRepository;

    @Mock
    private ProjectCollaborationStateRepository stateRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CollaborationSnapshotQueryService collaborationSnapshotQueryService;

    @Test
    @DisplayName("초기 version 요청에 현재 materialized graph를 반환한다")
    void getSnapshot_withInitialVersion_returnsMaterializedGraphAtCurrentVersion() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project()));
        when(snapshotRepository.findTopByProjectIdOrderByServerVersionDesc(projectId))
                .thenReturn(Optional.empty());
        when(projectNodeRepository.findAllByProjectId(projectId)).thenReturn(List.of());
        when(projectEdgeRepository.findAllByProjectId(projectId)).thenReturn(List.of());
        when(operationRepository.findAllByProjectIdOrderByServerVersionAsc(projectId))
                .thenReturn(List.of(operation(1L), operation(3L)));

        // when
        CollaborationSnapshotResDTO.SnapshotResDTO result =
                collaborationSnapshotQueryService.getSnapshot(projectId, memberId, 0L);

        // then
        assertEquals(projectId, result.project().projectId());
        assertEquals(3L, result.serverVersion());
        assertEquals(3L, result.graphVersion());
        assertEquals(0, result.operations().size());
        verify(projectAccessService).requireReadAccess(projectId, memberId);
    }

    @Test
    @DisplayName("보유 version 이후의 operation만 반환한다")
    void getSnapshot_withExistingVersion_returnsOnlyOperationsAfterVersion() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project()));
        when(operationRepository.findAllByProjectIdOrderByServerVersionAsc(projectId))
                .thenReturn(List.of(operation(1L), operation(3L)));

        // when
        CollaborationSnapshotResDTO.SnapshotResDTO result =
                collaborationSnapshotQueryService.getSnapshot(projectId, memberId, 1L);

        // then
        assertNull(result.project());
        assertEquals(1L, result.graphVersion());
        assertEquals(3L, result.serverVersion());
        assertEquals(1, result.operations().size());
        assertEquals(3L, result.operations().get(0).serverVersion());
    }

    @Test
    @DisplayName("compact된 operation log에서는 snapshot과 이후 operation을 반환한다")
    void getSnapshot_withCompactedOperationLog_returnsSnapshotAndFollowingOperations() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        ProjectCollaborationSnapshot snapshot = snapshot(3L);
        ProjectResDTO.ProjectDetailResDTO snapshotProject = ProjectResDTO.ProjectDetailResDTO.builder()
                .projectId(projectId)
                .title("snapshot")
                .build();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project()));
        when(stateRepository.findByProjectId(projectId)).thenReturn(Optional.of(state(5L)));
        when(operationRepository.findAllByProjectIdOrderByServerVersionAsc(projectId))
                .thenReturn(List.of(operation(5L)));
        when(snapshotRepository.findTopByProjectIdOrderByServerVersionDesc(projectId))
                .thenReturn(Optional.of(snapshot));
        when(objectMapper.convertValue(
                snapshot.getGraphPayload(),
                ProjectResDTO.ProjectDetailResDTO.class
        )).thenReturn(snapshotProject);

        // when
        CollaborationSnapshotResDTO.SnapshotResDTO result =
                collaborationSnapshotQueryService.getSnapshot(projectId, memberId, 1L);

        // then
        assertEquals(snapshotProject, result.project());
        assertEquals(3L, result.graphVersion());
        assertEquals(5L, result.serverVersion());
        assertEquals(1, result.operations().size());
        assertEquals(5L, result.operations().get(0).serverVersion());
    }

    @Test
    @DisplayName("저장된 snapshot과 snapshot 이후 operation을 반환한다")
    void getSnapshot_withStoredSnapshot_returnsSnapshotAndOperationsAfterSnapshotVersion() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        ProjectCollaborationSnapshot snapshot = snapshot(1L);
        ProjectResDTO.ProjectDetailResDTO snapshotProject = ProjectResDTO.ProjectDetailResDTO.builder()
                .projectId(projectId)
                .title("snapshot")
                .build();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project()));
        when(operationRepository.findAllByProjectIdOrderByServerVersionAsc(projectId))
                .thenReturn(List.of(operation(1L), operation(3L)));
        when(snapshotRepository.findTopByProjectIdOrderByServerVersionDesc(projectId))
                .thenReturn(Optional.of(snapshot));
        when(objectMapper.convertValue(
                snapshot.getGraphPayload(),
                ProjectResDTO.ProjectDetailResDTO.class
        )).thenReturn(snapshotProject);

        // when
        CollaborationSnapshotResDTO.SnapshotResDTO result =
                collaborationSnapshotQueryService.getSnapshot(projectId, memberId, 0L);

        // then
        assertEquals(snapshotProject, result.project());
        assertEquals(1L, result.graphVersion());
        assertEquals(3L, result.serverVersion());
        assertEquals(1, result.operations().size());
        assertEquals(3L, result.operations().get(0).serverVersion());
    }

    @Test
    @DisplayName("현재 serverVersion보다 미래인 version 요청을 거부한다")
    void getSnapshot_withFutureVersion_throwsVersionConflict() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project()));
        when(operationRepository.findAllByProjectIdOrderByServerVersionAsc(projectId))
                .thenReturn(List.of(operation(1L)));

        // when
        CollaborationException exception = assertThrows(
                CollaborationException.class,
                () -> collaborationSnapshotQueryService.getSnapshot(projectId, memberId, 2L)
        );

        // then
        assertEquals(CollaborationErrorCode.VERSION_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("음수 afterVersion 요청을 거부한다")
    void getSnapshot_withInvalidAfterVersion_throwsInvalidOperation() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;

        // when
        CollaborationException exception = assertThrows(
                CollaborationException.class,
                () -> collaborationSnapshotQueryService.getSnapshot(projectId, memberId, -1L)
        );

        // then
        assertEquals(CollaborationErrorCode.INVALID_OPERATION, exception.getCode());
    }

    @Test
    @DisplayName("읽기 권한이 없으면 snapshot 조회를 거부한다")
    void getSnapshot_withoutReadAccess_throwsProjectException() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        ProjectException accessDenied = new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED);
        doThrow(accessDenied).when(projectAccessService).requireReadAccess(projectId, memberId);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> collaborationSnapshotQueryService.getSnapshot(projectId, memberId, 0L)
        );

        // then
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, exception.getCode());
    }

    private Project project() {
        Project project = Project.builder()
                .title("project")
                .status(ProjectStatus.DRAFT)
                .build();
        ReflectionTestUtils.setField(project, "id", 1L);
        return project;
    }

    private ProjectCollaborationOperation operation(Long serverVersion) {
        return ProjectCollaborationOperation.builder()
                .project(project())
                .actorMember(com.infragen.infragen.domain.member.entity.Member.builder()
                        .isActive(true)
                        .build())
                .operationId("op-" + serverVersion)
                .clientId("client-1")
                .baseVersion(serverVersion - 1)
                .serverVersion(serverVersion)
                .operationType(CollaborationOperationType.UPDATE_NODE_NAME)
                .nodeId("node-1")
                .payload(Map.of("value", "database"))
                .build();
    }

    private ProjectCollaborationSnapshot snapshot(Long serverVersion) {
        return ProjectCollaborationSnapshot.builder()
                .project(project())
                .updatedBy(Member.builder().isActive(true).build())
                .serverVersion(serverVersion)
                .graphPayload(Map.of("projectId", 1L))
                .build();
    }

    private ProjectCollaborationState state(Long serverVersion) {
        ProjectCollaborationState state = new ProjectCollaborationState(project());
        ReflectionTestUtils.setField(state, "serverVersion", serverVersion);
        return state;
    }

}
