package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationOperationReqDTO;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.collaboration.validator.CollaborationOperationValidator;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private ProjectCollaborationVersionService projectCollaborationVersionService;

    @Mock
    private ProjectCollaborationOperationRepository operationRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectNodeRepository projectNodeRepository;

    @Mock
    private MemberQueryService memberQueryService;

    @InjectMocks
    private CollaborationOperationCommandService collaborationOperationCommandService;

    @Test
    void recordOperation_withValidOperation_savesOperationLog() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        CollaborationOperationReqDTO.Operation operation = operation();
        Project project = project();
        Member member = member();
        ProjectNode targetNode = node();

        when(operationRepository.findByProjectIdAndOperationId(projectId, operation.operationId()))
                .thenReturn(Optional.empty());
        when(projectCollaborationVersionService.issueNextVersion(projectId, operation.baseVersion())).thenReturn(1L);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(memberQueryService.findById(memberId)).thenReturn(member);
        when(projectNodeRepository.findByProjectIdAndNodeId(projectId, operation.nodeId()))
                .thenReturn(Optional.of(targetNode));
        when(operationRepository.save(any(ProjectCollaborationOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Optional<CollaborationOperationResDTO.BroadcastOperationResDTO> result = collaborationOperationCommandService.recordOperation(
                projectId,
                memberId,
                operation
        );

        // then
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().serverVersion());
        assertEquals("database", targetNode.getNodeName());
        verify(projectAccessService).requireWriteAccess(projectId, memberId);
        verify(collaborationOperationValidator).validate(operation);
        verify(projectCollaborationVersionService).issueNextVersion(projectId, operation.baseVersion());
        verify(operationRepository).save(any(ProjectCollaborationOperation.class));
    }

    @Test
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
                () -> collaborationOperationCommandService.recordOperation(projectId, memberId, operation)
        );

        // then
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, exception.getCode());
        verify(operationRepository, never()).save(any());
    }

    @Test
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
                () -> collaborationOperationCommandService.recordOperation(projectId, memberId, operation)
        );

        // then
        assertEquals(CollaborationErrorCode.INVALID_OPERATION_PAYLOAD, exception.getCode());
        verify(projectCollaborationVersionService, never()).issueNextVersion(projectId, operation.baseVersion());
    }

    @Test
    void recordOperation_withSameOperationIdAndContent_returnsExistingVersion() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        CollaborationOperationReqDTO.Operation operation = operation();
        ProjectCollaborationOperation existing = ProjectCollaborationOperation.builder()
                .project(project())
                .actorMember(member())
                .operationId(operation.operationId())
                .clientId(operation.clientId())
                .baseVersion(operation.baseVersion())
                .serverVersion(7L)
                .operationType(operation.type())
                .nodeId(operation.nodeId())
                .payload(operation.payload())
                .build();
        when(operationRepository.findByProjectIdAndOperationId(projectId, operation.operationId()))
                .thenReturn(Optional.of(existing));

        // when
        Optional<CollaborationOperationResDTO.BroadcastOperationResDTO> result = collaborationOperationCommandService.recordOperation(
                projectId,
                memberId,
                operation
        );

        // then
        assertFalse(result.isPresent());
        verify(projectCollaborationVersionService, never()).issueNextVersion(projectId, operation.baseVersion());
        verify(operationRepository, never()).save(any());
    }

    @Test
    void recordOperation_withoutTargetNode_throwsNotFound() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        CollaborationOperationReqDTO.Operation operation = operation();
        when(operationRepository.findByProjectIdAndOperationId(projectId, operation.operationId()))
                .thenReturn(Optional.empty());
        when(projectNodeRepository.findByProjectIdAndNodeId(projectId, operation.nodeId()))
                .thenReturn(Optional.empty());

        // when
        CollaborationException exception = assertThrows(
                CollaborationException.class,
                () -> collaborationOperationCommandService.recordOperation(projectId, memberId, operation)
        );

        // then
        assertEquals(CollaborationErrorCode.TARGET_NOT_FOUND, exception.getCode());
        verify(projectCollaborationVersionService, never()).issueNextVersion(projectId, operation.baseVersion());
    }

    @Test
    void recordOperation_withReusedOperationId_throwsConflict() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        CollaborationOperationReqDTO.Operation operation = operation();
        ProjectCollaborationOperation existing = ProjectCollaborationOperation.builder()
                .project(project())
                .actorMember(member())
                .operationId(operation.operationId())
                .clientId("another-client")
                .baseVersion(operation.baseVersion())
                .serverVersion(7L)
                .operationType(operation.type())
                .nodeId(operation.nodeId())
                .payload(operation.payload())
                .build();
        when(operationRepository.findByProjectIdAndOperationId(projectId, operation.operationId()))
                .thenReturn(Optional.of(existing));

        // when
        CollaborationException exception = assertThrows(
                CollaborationException.class,
                () -> collaborationOperationCommandService.recordOperation(projectId, memberId, operation)
        );

        // then
        assertEquals(CollaborationErrorCode.OPERATION_ID_REUSED, exception.getCode());
    }

    @Test
    void recordOperation_withFutureBaseVersion_doesNotChangeTargetNode() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        CollaborationOperationReqDTO.Operation operation = operation();
        ProjectNode targetNode = node();
        CollaborationException versionConflict = new CollaborationException(
                CollaborationErrorCode.VERSION_CONFLICT
        );
        when(operationRepository.findByProjectIdAndOperationId(projectId, operation.operationId()))
                .thenReturn(Optional.empty());
        when(projectNodeRepository.findByProjectIdAndNodeId(projectId, operation.nodeId()))
                .thenReturn(Optional.of(targetNode));
        doThrow(versionConflict).when(projectCollaborationVersionService)
                .issueNextVersion(projectId, operation.baseVersion());

        // when
        CollaborationException exception = assertThrows(
                CollaborationException.class,
                () -> collaborationOperationCommandService.recordOperation(projectId, memberId, operation)
        );

        // then
        assertEquals(CollaborationErrorCode.VERSION_CONFLICT, exception.getCode());
        assertEquals("mysql", targetNode.getNodeName());
        verify(operationRepository, never()).save(any());
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

    private Project project() {
        return Project.builder().title("project").build();
    }

    private Member member() {
        return Member.builder().isActive(true).build();
    }

    private ProjectNode node() {
        return ProjectNode.builder()
                .nodeName("mysql")
                .nodeId("node-1")
                .positionX(java.math.BigDecimal.ZERO)
                .positionY(java.math.BigDecimal.ZERO)
                .build();
    }
}
