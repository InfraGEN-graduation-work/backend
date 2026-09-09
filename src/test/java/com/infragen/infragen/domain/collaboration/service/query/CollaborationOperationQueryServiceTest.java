package com.infragen.infragen.domain.collaboration.service.query;

import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationOperationQueryServiceTest {
    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private ProjectCollaborationOperationRepository operationRepository;

    @InjectMocks
    private CollaborationOperationQueryService collaborationOperationQueryService;

    @Test
    void getOperationsAfterVersion_returnsOnlyLaterOperationsInOrder() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        ProjectCollaborationOperation first = operation(1L, "database");
        ProjectCollaborationOperation second = operation(3L, "primary-database");
        when(operationRepository.findAllByProjectIdOrderByServerVersionAsc(projectId))
                .thenReturn(List.of(first, second));

        // when
        List<CollaborationOperationResDTO.BroadcastOperationResDTO> result =
                collaborationOperationQueryService.getOperationsAfterVersion(projectId, memberId, 1L);

        // then
        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).serverVersion());
        assertEquals("primary-database", result.get(0).payload().get("value"));
        verify(projectAccessService).requireReadAccess(projectId, memberId);
    }

    @Test
    void getOperationsAfterVersion_withInvalidVersion_throwsInvalidOperation() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;

        // when
        CollaborationException exception = assertThrows(
                CollaborationException.class,
                () -> collaborationOperationQueryService.getOperationsAfterVersion(
                        projectId,
                        memberId,
                        -1L
                )
        );

        // then
        assertEquals(CollaborationErrorCode.INVALID_OPERATION, exception.getCode());
    }

    private ProjectCollaborationOperation operation(Long serverVersion, String nodeName) {
        Member member = Member.builder().isActive(true).build();
        ReflectionTestUtils.setField(member, "id", 2L);

        return ProjectCollaborationOperation.builder()
                .project(Project.builder().title("project").build())
                .actorMember(member)
                .operationId("op-" + serverVersion)
                .clientId("client-1")
                .baseVersion(serverVersion - 1)
                .serverVersion(serverVersion)
                .operationType(CollaborationOperationType.UPDATE_NODE_NAME)
                .nodeId("node-1")
                .payload(Map.of("value", nodeName))
                .build();
    }
}
