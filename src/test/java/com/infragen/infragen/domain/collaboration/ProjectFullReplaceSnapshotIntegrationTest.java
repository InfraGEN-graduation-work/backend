package com.infragen.infragen.domain.collaboration;

import com.infragen.infragen.domain.collaboration.converter.ProjectCollaborationSnapshotConverter;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationSnapshotResDTO;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationSnapshot;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationState;
import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;
import com.infragen.infragen.domain.collaboration.event.ProjectRoomResyncPublisher;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationSnapshotRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationStateRepository;
import com.infragen.infragen.domain.collaboration.service.command.ProjectCollaborationCheckpointFailureService;
import com.infragen.infragen.domain.collaboration.service.command.CollaborationOperationCompactionService;
import com.infragen.infragen.domain.collaboration.service.command.ProjectCollaborationSnapshotCommandService;
import com.infragen.infragen.domain.collaboration.service.command.ProjectCollaborationSnapshotWriter;
import com.infragen.infragen.domain.collaboration.service.command.ProjectCollaborationVersionService;
import com.infragen.infragen.domain.collaboration.service.query.CollaborationSnapshotQueryService;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.converter.ProjectConverter;
import com.infragen.infragen.domain.project.dto.request.ProjectEdgeReqDTO;
import com.infragen.infragen.domain.project.dto.request.ProjectNodeReqDTO;
import com.infragen.infragen.domain.project.dto.request.ProjectReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.repository.GeneratedFileRepository;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.repository.ProjectHistoryRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.service.command.ProjectCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DB를 mock으로 격리하고 실제 PUT 서비스와 Spring transaction event의 복원·broadcast 계약을 검증한다.
 * DB 원자성과 row lock 동시성은 MySQL integration test에서 별도로 검증해야 한다.
 */
@SpringJUnitConfig(ProjectFullReplaceSnapshotIntegrationTest.Config.class)
class ProjectFullReplaceSnapshotIntegrationTest {
    @Autowired
    private ProjectCommandService projectCommandService;
    @Autowired
    private CollaborationSnapshotQueryService snapshotQueryService;
    @Autowired
    private CollaborationOperationCompactionService compactionService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RecordingTransactionManager transactionManager;

    @MockitoBean
    private ProjectRepository projectRepository;
    @MockitoBean
    private ProjectNodeRepository nodeRepository;
    @MockitoBean
    private ProjectEdgeRepository edgeRepository;
    @MockitoBean
    private ProjectHistoryRepository historyRepository;
    @MockitoBean
    private GeneratedFileRepository generatedFileRepository;
    @MockitoBean
    private MemberQueryService memberQueryService;
    @MockitoBean
    private ProjectQueryService projectQueryService;
    @MockitoBean
    private ProjectAccessService projectAccessService;
    @MockitoBean
    private ProjectCollaborationVersionService versionService;
    @MockitoBean
    private ProjectCollaborationSnapshotRepository snapshotRepository;
    @MockitoBean
    private ProjectCollaborationOperationRepository operationRepository;
    @MockitoBean
    private ProjectCollaborationStateRepository stateRepository;
    @MockitoBean
    private ProjectCollaborationSnapshotWriter snapshotWriter;
    @MockitoBean
    private ProjectCollaborationCheckpointFailureService failureService;
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private Member owner;
    private Project project;
    private ProjectCollaborationState state;
    private List<ProjectCollaborationSnapshot> snapshots;
    private List<ProjectCollaborationOperation> operations;

    @BeforeEach
    void setUp() {
        owner = Member.builder().isActive(true).build();
        ReflectionTestUtils.setField(owner, "id", 2L);
        project = Project.builder().member(owner).title("old-title").description("old-description")
                .status(ProjectStatus.DRAFT).build();
        ReflectionTestUtils.setField(project, "id", 1L);
        state = new ProjectCollaborationState(project);
        operations = new ArrayList<>();
        for (long version = 1; version <= 50; version++) {
            state.advanceServerVersion();
            operations.add(operation(version));
        }
        snapshots = new ArrayList<>(List.of(ProjectCollaborationSnapshot.builder()
                .project(project)
                .updatedBy(owner)
                .serverVersion(50L)
                .graphPayload(ProjectCollaborationSnapshotConverter.toGraphPayload(
                        ProjectConverter.toProjectDetailResDTO(project, List.of(), List.of()), objectMapper))
                .build()));
        transactionManager.commits = 0;
        transactionManager.rollbacks = 0;

        when(projectQueryService.getOwnedProject(1L, 2L)).thenReturn(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(versionService.issueNextVersionForFullReplace(1L, 50L)).thenAnswer(invocation -> {
            state.advanceServerVersion();
            return state.getServerVersion();
        });
        when(nodeRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(edgeRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotRepository.save(any())).thenAnswer(invocation -> {
            ProjectCollaborationSnapshot snapshot = invocation.getArgument(0);
            snapshots.add(snapshot);
            return snapshot;
        });
        when(snapshotRepository.findTopByProjectIdOrderByServerVersionDesc(1L)).thenAnswer(invocation ->
                snapshots.stream().max(Comparator.comparing(ProjectCollaborationSnapshot::getServerVersion)));
        when(stateRepository.findByProjectId(1L)).thenReturn(Optional.of(state));
        when(operationRepository.findAllByProjectIdOrderByServerVersionAsc(1L)).thenReturn(operations);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 49L, 50L})
    @DisplayName("snapshot v50 뒤 PUT v51을 저장하면 초기·이전 version 재접속 모두 새 graph를 복원한다")
    void updateProject_AfterSnapshot_RestoresPutGraph(long afterVersion) {
        // given
        ProjectReqDTO.UpdateProjectReqDTO request = updateRequest();
        doAnswer(invocation -> {
            assertEquals(1, transactionManager.commits);
            return null;
        }).when(messagingTemplate).convertAndSend(eq("/topic/projects/1/resync"), any(Object.class));

        // when
        ProjectResDTO.ProjectDetailResDTO updated = projectCommandService.updateProject(1L, request, 2L);
        CollaborationSnapshotResDTO.SnapshotResDTO restored = snapshotQueryService.getSnapshot(1L, 2L, afterVersion);

        // then
        ArgumentCaptor<CollaborationSnapshotResDTO.SnapshotResDTO> broadcast =
                ArgumentCaptor.forClass(CollaborationSnapshotResDTO.SnapshotResDTO.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/projects/1/resync"), broadcast.capture());
        assertAll(
                () -> assertEquals(updated, restored.project()),
                () -> assertEquals(51L, restored.graphVersion()),
                () -> assertEquals(51L, restored.serverVersion()),
                () -> assertTrue(restored.operations().isEmpty()),
                () -> assertEquals(restored, broadcast.getValue()),
                () -> assertEquals(51L, snapshots.getLast().getServerVersion()),
                () -> assertSame(owner, snapshots.getLast().getUpdatedBy())
        );
        verifyNoInteractions(snapshotWriter, failureService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 50L})
    @DisplayName("PUT 뒤 operation이 있으면 PUT snapshot과 그 이후 operation만 복원한다")
    void getSnapshot_OperationAfterPut_ReplaysOnlyAfterPut(long afterVersion) {
        // given
        ProjectResDTO.ProjectDetailResDTO updated = projectCommandService.updateProject(1L, updateRequest(), 2L);
        state.advanceServerVersion();
        operations.add(operation(52L));

        // when
        CollaborationSnapshotResDTO.SnapshotResDTO restored = snapshotQueryService.getSnapshot(1L, 2L, afterVersion);

        // then
        assertAll(
                () -> assertEquals(updated, restored.project()),
                () -> assertEquals(51L, restored.graphVersion()),
                () -> assertEquals(52L, restored.serverVersion()),
                () -> assertEquals(List.of(52L), restored.operations().stream()
                        .map(operation -> operation.serverVersion()).toList()),
                () -> assertEquals(Map.of("value", "name-52"), restored.operations().getFirst().payload())
        );
    }

    @Test
    @DisplayName("PUT version을 이미 보유한 client에는 이후 operation만 전달한다")
    void getSnapshot_ClientHasPutVersion_ReturnsDelta() {
        // given
        projectCommandService.updateProject(1L, updateRequest(), 2L);
        state.advanceServerVersion();
        operations.add(operation(52L));

        // when
        CollaborationSnapshotResDTO.SnapshotResDTO restored = snapshotQueryService.getSnapshot(1L, 2L, 51L);

        // then
        assertAll(
                () -> assertNull(restored.project()),
                () -> assertEquals(51L, restored.graphVersion()),
                () -> assertEquals(52L, restored.serverVersion()),
                () -> assertEquals(List.of(52L), restored.operations().stream()
                        .map(operation -> operation.serverVersion()).toList())
        );
    }

    @Test
    @DisplayName("PUT 이전 log를 compact해도 PUT snapshot과 이후 operation으로 복원한다")
    void getSnapshot_CompactedAfterPut_RestoresSnapshotAndReplay() {
        // given
        ProjectResDTO.ProjectDetailResDTO updated = projectCommandService.updateProject(1L, updateRequest(), 2L);
        state.advanceServerVersion();
        operations.add(operation(52L));
        when(operationRepository.deleteAllByProjectIdAndServerVersionLessThanEqual(1L, 51L))
                .thenAnswer(invocation -> {
                    int previousSize = operations.size();
                    operations.removeIf(operation -> operation.getServerVersion() <= 51L);
                    return previousSize - operations.size();
                });

        // when
        int deleted = compactionService.compact(1L);
        CollaborationSnapshotResDTO.SnapshotResDTO restored = snapshotQueryService.getSnapshot(1L, 2L, 49L);

        // then
        assertAll(
                () -> assertEquals(50, deleted),
                () -> assertEquals(updated, restored.project()),
                () -> assertEquals(51L, restored.graphVersion()),
                () -> assertEquals(52L, restored.serverVersion()),
                () -> assertEquals(List.of(52L), restored.operations().stream()
                        .map(operation -> operation.serverVersion()).toList())
        );
    }

    @Test
    @DisplayName("설명이 없는 빈 graph로 PUT해도 snapshot을 저장하고 빈 graph를 복원한다")
    void updateProject_EmptyGraphWithoutDescription_RestoresEmptyGraph() {
        // given
        ProjectReqDTO.UpdateProjectReqDTO request = new ProjectReqDTO.UpdateProjectReqDTO(
                "empty-project", null, List.of(), List.of(), 50L);

        // when
        ProjectResDTO.ProjectDetailResDTO updated = projectCommandService.updateProject(1L, request, 2L);
        CollaborationSnapshotResDTO.SnapshotResDTO restored = snapshotQueryService.getSnapshot(1L, 2L, 0L);

        // then
        assertAll(
                () -> assertEquals(updated, restored.project()),
                () -> assertNull(restored.project().description()),
                () -> assertTrue(restored.project().nodes().isEmpty()),
                () -> assertTrue(restored.project().edges().isEmpty()),
                () -> assertEquals(51L, restored.graphVersion()),
                () -> assertEquals(51L, restored.serverVersion())
        );
    }

    @Test
    @DisplayName("PUT snapshot 저장 실패는 transaction 롤백으로 전파되고 room resync를 보내지 않는다")
    void updateProject_SnapshotFailure_RollsBackWithoutBroadcast() {
        // given
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("snapshot unavailable");
        doThrow(failure).when(snapshotRepository).save(any());

        // when
        DataAccessResourceFailureException thrown = assertThrows(DataAccessResourceFailureException.class,
                () -> projectCommandService.updateProject(1L, updateRequest(), 2L));

        // then
        assertAll(
                () -> assertSame(failure, thrown),
                () -> assertEquals(0, transactionManager.commits),
                () -> assertEquals(1, transactionManager.rollbacks),
                () -> assertEquals(1, snapshots.size())
        );
        verifyNoInteractions(messagingTemplate, snapshotWriter, failureService);
    }

    private ProjectReqDTO.UpdateProjectReqDTO updateRequest() {
        return new ProjectReqDTO.UpdateProjectReqDTO(
                "new-title", "new-description",
                List.of(
                        new ProjectNodeReqDTO.NodeInfoReqDTO("node-1", "new-database", "MYSQL",
                                BigDecimal.ONE, BigDecimal.TEN, Map.of("port", 3306)),
                        new ProjectNodeReqDTO.NodeInfoReqDTO("node-2", "new-application", "SPRING_BOOT",
                                BigDecimal.TEN, BigDecimal.ONE, Map.of("port", 8080))
                ),
                List.of(new ProjectEdgeReqDTO.EdgeInfoReqDTO("node-1", "node-2")),
                50L
        );
    }

    private ProjectCollaborationOperation operation(long version) {
        return ProjectCollaborationOperation.builder()
                .project(project).actorMember(owner).operationId("operation-" + version).clientId("client-1")
                .baseVersion(version - 1).serverVersion(version)
                .operationType(CollaborationOperationType.UPDATE_NODE_NAME).nodeId("node-1")
                .payload(Map.of("value", "name-" + version)).build();
    }

    @Configuration
    @EnableTransactionManagement
    @Import({ProjectCommandService.class, ProjectCollaborationSnapshotCommandService.class,
            CollaborationSnapshotQueryService.class, ProjectRoomResyncPublisher.class,
            CollaborationOperationCompactionService.class})
    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }
    }

    // 실제 Spring synchronization을 실행하되 DB commit·rollback 대신 호출 횟수만 기록한다.
    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private int commits;
        private int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
