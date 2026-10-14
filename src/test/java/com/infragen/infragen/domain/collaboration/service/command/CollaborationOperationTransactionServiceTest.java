package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationOperationReqDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationOperationTransactionServiceTest {
    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 2L;

    @Mock
    private ProjectCollaborationVersionService projectCollaborationVersionService;

    @Mock
    private ProjectCollaborationOperationRepository operationRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectNodeRepository projectNodeRepository;

    @Mock
    private ProjectEdgeRepository projectEdgeRepository;

    @Mock
    private MemberQueryService memberQueryService;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CollaborationOperationTransactionService transactionService;

    @Test
    @DisplayName("lock 뒤 쓰기 권한이 있으면 operation을 저장하고 방송 결과를 반환한다")
    void recordNewOperation_withWriteAccessAfterLock_savesOperation() {
        // given
        CollaborationOperationReqDTO.Operation operation = operation();
        ProjectNode targetNode = mock(ProjectNode.class);
        when(projectCollaborationVersionService.issueNextVersion(PROJECT_ID, operation.baseVersion(), operation.operationId()))
                .thenReturn(new ProjectCollaborationVersionService.VersionIssuance(5L, null));
        when(projectNodeRepository.findByProjectIdAndNodeId(PROJECT_ID, operation.nodeId()))
                .thenReturn(Optional.of(targetNode));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(mock(Project.class)));
        when(memberQueryService.findById(MEMBER_ID)).thenReturn(mock(Member.class));
        when(operationRepository.save(any(ProjectCollaborationOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Optional<CollaborationOperationResDTO.BroadcastOperationResDTO> result =
                transactionService.recordNewOperation(PROJECT_ID, MEMBER_ID, operation);

        // then
        assertTrue(result.isPresent());
        assertEquals(5L, result.get().serverVersion());
        InOrder lockOrder = inOrder(projectCollaborationVersionService, projectAccessService, operationRepository);
        lockOrder.verify(projectCollaborationVersionService)
                .issueNextVersion(PROJECT_ID, operation.baseVersion(), operation.operationId());
        lockOrder.verify(projectAccessService).requireWriteAccess(PROJECT_ID, MEMBER_ID);
        lockOrder.verify(operationRepository).save(any(ProjectCollaborationOperation.class));
        verify(targetNode).moveTo(new BigDecimal("10"), new BigDecimal("20"));
    }

    @Test
    @DisplayName("lock 뒤 쓰기 권한이 회수됐으면 node 변경과 operation 저장 없이 거부한다")
    void recordNewOperation_withRevokedAccessAfterLock_throwsWithoutWriting() {
        // given
        CollaborationOperationReqDTO.Operation operation = operation();
        when(projectCollaborationVersionService.issueNextVersion(PROJECT_ID, operation.baseVersion(), operation.operationId()))
                .thenReturn(new ProjectCollaborationVersionService.VersionIssuance(5L, null));
        doThrow(new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).requireWriteAccess(PROJECT_ID, MEMBER_ID);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> transactionService.recordNewOperation(PROJECT_ID, MEMBER_ID, operation)
        );

        // then
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, exception.getCode());
        InOrder lockOrder = inOrder(projectCollaborationVersionService, projectAccessService);
        lockOrder.verify(projectCollaborationVersionService)
                .issueNextVersion(PROJECT_ID, operation.baseVersion(), operation.operationId());
        lockOrder.verify(projectAccessService).requireWriteAccess(PROJECT_ID, MEMBER_ID);
        verify(projectNodeRepository, never()).findByProjectIdAndNodeId(any(), any());
        verify(operationRepository, never()).save(any(ProjectCollaborationOperation.class));
    }

    @Test
    @DisplayName("lock 뒤 쓰기 권한이 회수됐으면 같은 operationId 재전송도 빈 결과 대신 거부한다")
    void recordNewOperation_withRevokedAccessOnRetry_throws() {
        // given
        CollaborationOperationReqDTO.Operation operation = operation();
        when(projectCollaborationVersionService.issueNextVersion(PROJECT_ID, operation.baseVersion(), operation.operationId()))
                .thenReturn(new ProjectCollaborationVersionService.VersionIssuance(
                        null,
                        mock(ProjectCollaborationOperation.class)
                ));
        doThrow(new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).requireWriteAccess(PROJECT_ID, MEMBER_ID);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> transactionService.recordNewOperation(PROJECT_ID, MEMBER_ID, operation)
        );

        // then
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, exception.getCode());
        verify(operationRepository, never()).save(any(ProjectCollaborationOperation.class));
    }

    private CollaborationOperationReqDTO.Operation operation() {
        return new CollaborationOperationReqDTO.Operation(
                "operation-1",
                "client-1",
                4L,
                CollaborationOperationType.UPDATE_NODE_POSITION,
                "node-1",
                Map.of("positionX", 10, "positionY", 20)
        );
    }
}
