package com.infragen.infragen.domain.collaboration.service.query;

import com.infragen.infragen.domain.collaboration.dto.response.CollaborationSnapshotResDTO;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @InjectMocks
    private CollaborationSnapshotQueryService collaborationSnapshotQueryService;

    @Test
    void getSnapshot_returnsMaterializedGraphAndOperationsAfterVersion() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project()));
        when(projectNodeRepository.findAllByProjectId(projectId)).thenReturn(List.of());
        when(projectEdgeRepository.findAllByProjectId(projectId)).thenReturn(List.of());
        when(operationRepository.findAllByProjectIdOrderByServerVersionAsc(projectId))
                .thenReturn(List.of(operation(1L), operation(3L)));

        // when
        CollaborationSnapshotResDTO.SnapshotResDTO result =
                collaborationSnapshotQueryService.getSnapshot(projectId, memberId, 1L);

        // then
        assertEquals(projectId, result.project().projectId());
        assertEquals(3L, result.serverVersion());
        assertEquals(1, result.operations().size());
        assertEquals(3L, result.operations().get(0).serverVersion());
        verify(projectAccessService).requireReadAccess(projectId, memberId);
    }

    @Test
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
}
