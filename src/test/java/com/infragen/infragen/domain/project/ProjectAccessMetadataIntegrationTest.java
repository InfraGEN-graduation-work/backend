package com.infragen.infragen.domain.project;

import com.infragen.infragen.domain.collaboration.dto.response.CollaborationSnapshotResDTO;
import com.infragen.infragen.domain.collaboration.dto.request.CollaborationOperationReqDTO;
import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;
import com.infragen.infragen.domain.collaboration.service.command.CollaborationOperationTransactionService;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationState;
import com.infragen.infragen.domain.collaboration.event.ProjectRoomResyncPublisher;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationStateRepository;
import com.infragen.infragen.domain.collaboration.service.command.ProjectCollaborationCheckpointFailureService;
import com.infragen.infragen.domain.collaboration.service.command.ProjectCollaborationSnapshotCommandService;
import com.infragen.infragen.domain.collaboration.service.command.ProjectCollaborationSnapshotWriter;
import com.infragen.infragen.domain.collaboration.service.command.ProjectCollaborationVersionService;
import com.infragen.infragen.domain.collaboration.service.query.CollaborationSnapshotQueryService;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.dto.request.ProjectReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectEdgeResDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectNodeResDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.entity.ProjectEdge;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.service.command.ProjectCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;
import com.infragen.infragen.global.enums.ComponentType;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 격리된 MySQL에서 목록 query와 metadata의 graph 보존·롤백·동시 버전 충돌을 검증한다. */
@Testcontainers
@DataJpaTest(properties = {
        "spring.docker.compose.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ProjectAccessMetadataIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProjectAccessMetadataIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse(
            "mysql:8.4.6@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9")
            .asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("infragen_issue55_test")
            .withUsername("infragen_test").withPassword("infragen_test_password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired EntityManager entityManager;
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectCollaboratorRepository collaboratorRepository;
    @Autowired ProjectCollaborationStateRepository stateRepository;
    @Autowired ProjectQueryService queryService;
    @Autowired ProjectCommandService commandService;
    @Autowired CollaborationSnapshotQueryService snapshotQueryService;
    @Autowired CollaborationOperationTransactionService operationService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MemberQueryService memberQueryService;
    @MockitoBean ProjectCollaborationSnapshotWriter snapshotWriter;
    @MockitoBean ProjectCollaborationCheckpointFailureService failureService;
    @MockitoBean SimpMessagingTemplate messagingTemplate;

    @Configuration
    @EnableJpaAuditing
    @EntityScan(basePackageClasses = {Project.class, Member.class, ProjectCollaborationState.class})
    @EnableJpaRepositories(basePackages = {
            "com.infragen.infragen.domain.project.repository",
            "com.infragen.infragen.domain.member.repository",
            "com.infragen.infragen.domain.collaboration.repository"
    })
    @Import({ProjectQueryService.class, ProjectCommandService.class, ProjectAccessService.class,
            ProjectCollaborationVersionService.class, ProjectCollaborationSnapshotCommandService.class,
            CollaborationSnapshotQueryService.class, ProjectRoomResyncPublisher.class,
            CollaborationOperationTransactionService.class})
    static class Config {
        @Bean ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Test
    @Transactional
    @DisplayName("owner·editor·viewer를 한 번에 최신순으로 조회하고 중복·비참여 프로젝트를 제외한다")
    void getProjects_MixedMembership_ReturnsUniqueOrderedPreviewsInOneQuery() {
        // given
        Member member = member();
        Member another = member();
        Member third = member();
        Project owned = project(member, "Owned", 1);
        Project edited = project(another, "Edited", 2);
        Project viewed = project(another, "Viewed", 3);
        Project hidden = project(third, "Hidden", 4);
        membership(owned, another, ProjectCollaboratorRole.EDITOR);
        membership(owned, third, ProjectCollaboratorRole.VIEWER);
        membership(edited, member, ProjectCollaboratorRole.EDITOR);
        membership(edited, third, ProjectCollaboratorRole.VIEWER);
        membership(viewed, member, ProjectCollaboratorRole.VIEWER);
        membership(hidden, another, ProjectCollaboratorRole.EDITOR);
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        // when
        var result = queryService.getProjects(member.getId()).projectList();

        // then
        assertAll(
                () -> assertEquals(List.of(viewed.getId(), edited.getId(), owned.getId()),
                        result.stream().map(row -> row.projectId()).toList()),
                () -> assertEquals(List.of("VIEWER", "EDITOR", "OWNER"),
                        result.stream().map(row -> row.accessRole()).toList()),
                () -> assertEquals(1L, statistics.getPrepareStatementCount())
        );
    }

    @Test
    @Transactional
    @DisplayName("collaborator 삭제 후 목록에서 제외되고 소유 프로젝트는 유지된다")
    void getProjects_RemovedMembership_ExcludesSharedProject() {
        // given
        Member member = member();
        Project owned = project(member, "Owned", 1);
        Project shared = project(member(), "Shared", 2);
        membership(shared, member, ProjectCollaboratorRole.EDITOR);
        entityManager.flush();
        assertEquals(2, queryService.getProjects(member.getId()).projectList().size());
        collaboratorRepository.deleteByProjectIdAndMemberId(shared.getId(), member.getId());
        entityManager.flush();
        entityManager.clear();

        // when
        var result = queryService.getProjects(member.getId()).projectList();

        // then
        assertEquals(List.of(owned.getId()), result.stream().map(row -> row.projectId()).toList());
    }

    @Test
    @Transactional
    @DisplayName("owner membership이 중복 저장된 과거 데이터에서도 OWNER 한 행을 반환한다")
    void getProjects_OwnerAlsoMembership_PrefersOwnerOnce() {
        // given
        Member owner = member();
        Project project = project(owner, "Owned", 1);
        membership(project, owner, ProjectCollaboratorRole.VIEWER);
        entityManager.flush();
        entityManager.clear();

        // when
        var result = queryService.getProjects(owner.getId()).projectList();

        // then
        assertAll(() -> assertEquals(1, result.size()),
                () -> assertEquals("OWNER", result.getFirst().accessRole()));
    }

    @Test
    @DisplayName("metadata PATCH는 DB graph를 보존하고 참여자의 snapshot과 resync에 반영된다")
    void updateMetadata_Owner_PreservesGraphAndRestoresForCollaborator() {
        // given
        Fixture fixture = fixture();
        var before = snapshotQueryService.getSnapshot(fixture.projectId(), fixture.editorId(), 0L);

        // when
        commandService.updateMetadata(fixture.projectId(),
                new ProjectReqDTO.UpdateMetadata("Renamed", null, 0L), fixture.ownerId());
        var restored = snapshotQueryService.getSnapshot(fixture.projectId(), fixture.editorId(), 0L);

        // then
        var broadcast = ArgumentCaptor.forClass(CollaborationSnapshotResDTO.SnapshotResDTO.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/projects/" + fixture.projectId() + "/resync"), broadcast.capture());
        var persisted = queryService.getProjectDetail(fixture.projectId(), fixture.ownerId());
        assertAll(
                () -> assertEquals("Renamed", persisted.title()),
                () -> assertEquals("Description", persisted.description()),
                () -> assertGraphEquivalent(before.project(), persisted),
                () -> assertEquals("Renamed", restored.project().title()),
                () -> assertEquals("Description", restored.project().description()),
                () -> assertGraphEquivalent(persisted, restored.project()),
                () -> assertEquals(1L, restored.serverVersion()),
                () -> assertEquals(restored.graphVersion(), broadcast.getValue().graphVersion()),
                () -> assertEquals(restored.serverVersion(), broadcast.getValue().serverVersion()),
                () -> assertEquals(restored.operations(), broadcast.getValue().operations()),
                () -> assertEquals(restored.project().title(), broadcast.getValue().project().title()),
                () -> assertEquals(restored.project().description(), broadcast.getValue().project().description()),
                () -> assertGraphEquivalent(restored.project(), broadcast.getValue().project())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDITOR", "VIEWER", "STRANGER"})
    @DisplayName("참여자는 snapshot을 읽을 수 있지만 metadata는 owner만 수정한다")
    void updateMetadata_NotOwner_Rejects(String role) {
        // given
        Fixture fixture = fixture();
        long memberId = switch (role) {
            case "EDITOR" -> fixture.editorId();
            case "VIEWER" -> fixture.viewerId();
            default -> fixture.strangerId();
        };
        if (!role.equals("STRANGER")) {
            assertNotNull(snapshotQueryService.getSnapshot(fixture.projectId(), memberId, 0L).project());
        }

        // when
        var exception = assertThrows(ProjectException.class, () -> commandService.updateMetadata(
                fixture.projectId(), new ProjectReqDTO.UpdateMetadata("Denied", null, 0L), memberId));

        // then
        assertAll(
                () -> assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode()),
                () -> assertEquals(0L, stateRepository.findByProjectId(fixture.projectId()).orElseThrow().getServerVersion())
        );
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("같은 baseVersion의 동시 metadata 변경은 하나만 commit된다")
    void updateMetadata_ConcurrentSameVersion_OneSuccessOneConflict() throws Exception {
        // given
        Fixture fixture = fixture();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> updateAfterStart(fixture, "First", start));
            var second = executor.submit(() -> updateAfterStart(fixture, "Second", start));

            // when
            start.countDown();
            var results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));

            // then
            assertAll(
                    () -> assertEquals(1L, results.stream().filter("SUCCESS"::equals).count()),
                    () -> assertEquals(1L, results.stream().filter("CONFLICT"::equals).count()),
                    () -> assertEquals(1L, stateRepository.findByProjectId(fixture.projectId()).orElseThrow().getServerVersion()),
                    () -> assertEquals(1, jdbc.queryForObject(
                            "SELECT COUNT(*) FROM project_collaboration_snapshot WHERE project_id = ?", Integer.class, fixture.projectId()))
            );
        }
    }

    @Test
    @DisplayName("metadata PATCH와 node 편집이 동시에 실행되어도 node 편집 결과를 보존한다")
    void updateMetadata_ConcurrentOperation_PreservesNodeEdit() throws Exception {
        // given
        Fixture fixture = fixture();
        when(memberQueryService.findById(fixture.editorId()))
                .thenAnswer(invocation -> entityManager.find(Member.class, fixture.editorId()));
        var operation = new CollaborationOperationReqDTO.Operation(
                UUID.randomUUID().toString(), "editor-client", 0L,
                CollaborationOperationType.UPDATE_NODE_NAME, "db", Map.of("value", "Edited database"));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var metadata = executor.submit(() -> updateAfterStart(fixture, "Renamed", start));
            var edit = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("동시 요청 시작 대기 초과");
                }
                return operationService.recordNewOperation(fixture.projectId(), fixture.editorId(), operation);
            });

            // when
            start.countDown();
            String metadataResult = metadata.get(20, TimeUnit.SECONDS);
            var operationResult = edit.get(20, TimeUnit.SECONDS);

            // then
            var persisted = queryService.getProjectDetail(fixture.projectId(), fixture.ownerId());
            assertAll(
                    () -> assertTrue(operationResult.isPresent()),
                    () -> assertEquals("Edited database", persisted.nodes().stream()
                            .filter(node -> node.nodeId().equals("db")).findFirst().orElseThrow().nodeName()),
                    () -> assertEquals(2, persisted.nodes().size()),
                    () -> assertEquals(1, persisted.edges().size()),
                    () -> assertEquals(metadataResult.equals("SUCCESS") ? "Renamed" : "Original", persisted.title())
            );
        }
    }

    @Test
    @DisplayName("MySQL snapshot 저장 실패 시 metadata·version을 롤백하고 graph를 보존한다")
    void updateMetadata_SnapshotInsertFailure_RollsBackDatabase() {
        // given
        Fixture fixture = fixture();
        var before = queryService.getProjectDetail(fixture.projectId(), fixture.ownerId());
        jdbc.execute("ALTER TABLE project_collaboration_snapshot ADD CONSTRAINT issue55_snapshot_failure CHECK (project_id <> "
                + fixture.projectId() + " OR server_version <> 1)");
        try {
            // when
            assertThrows(DataIntegrityViolationException.class, () -> commandService.updateMetadata(fixture.projectId(),
                    new ProjectReqDTO.UpdateMetadata("Fail", "Fail", 0L), fixture.ownerId()));

            // then
            assertAll(
                    () -> assertEquals(before, queryService.getProjectDetail(fixture.projectId(), fixture.ownerId())),
                    () -> assertEquals(0L, stateRepository.findByProjectId(fixture.projectId()).orElseThrow().getServerVersion()),
                    () -> assertEquals(0, jdbc.queryForObject(
                            "SELECT COUNT(*) FROM project_collaboration_snapshot WHERE project_id = ?", Integer.class, fixture.projectId()))
            );
            verifyNoInteractions(messagingTemplate);
        } finally {
            jdbc.execute("ALTER TABLE project_collaboration_snapshot DROP CHECK issue55_snapshot_failure");
        }
    }

    private String updateAfterStart(Fixture fixture, String title, CountDownLatch start) throws InterruptedException {
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("동시 요청 시작 대기 초과");
        }
        try {
            commandService.updateMetadata(fixture.projectId(),
                    new ProjectReqDTO.UpdateMetadata(title, null, 0L), fixture.ownerId());
            return "SUCCESS";
        } catch (CollaborationException exception) {
            assertEquals(CollaborationErrorCode.VERSION_CONFLICT, exception.getCode());
            return "CONFLICT";
        }
    }

    private Fixture fixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Member owner = member();
            Member editor = member();
            Member viewer = member();
            Member stranger = member();
            Project project = project(owner, "Original", 1);
            membership(project, editor, ProjectCollaboratorRole.EDITOR);
            membership(project, viewer, ProjectCollaboratorRole.VIEWER);
            ProjectNode source = node(project, "db", ComponentType.MYSQL);
            ProjectNode target = node(project, "app", ComponentType.SPRING_BOOT);
            entityManager.persist(ProjectEdge.builder().project(project).sourceNode(source).targetNode(target).build());
            entityManager.persist(new ProjectCollaborationState(project));
            entityManager.flush();
            entityManager.clear();
            return new Fixture(project.getId(), owner.getId(), editor.getId(), viewer.getId(), stranger.getId());
        });
    }

    private void assertGraphEquivalent(
            ProjectResDTO.ProjectDetailResDTO expected,
            ProjectResDTO.ProjectDetailResDTO actual
    ) {
        assertAll(
                () -> assertEquals(expected.projectId(), actual.projectId()),
                () -> assertEquals(
                        expected.nodes().stream().map(ProjectNodeResDTO.NodeInfoResDTO::nodeId).sorted().toList(),
                        actual.nodes().stream().map(ProjectNodeResDTO.NodeInfoResDTO::nodeId).sorted().toList()
                ),
                () -> assertEquals(edgeKeys(expected.edges()), edgeKeys(actual.edges()))
        );

        Map<String, ProjectNodeResDTO.NodeInfoResDTO> actualNodes = actual.nodes().stream()
                .collect(Collectors.toMap(ProjectNodeResDTO.NodeInfoResDTO::nodeId, node -> node));
        for (ProjectNodeResDTO.NodeInfoResDTO expectedNode : expected.nodes()) {
            ProjectNodeResDTO.NodeInfoResDTO actualNode = actualNodes.get(expectedNode.nodeId());
            assertNotNull(actualNode, "Missing node " + expectedNode.nodeId());
            assertAll(
                    () -> assertEquals(expectedNode.nodeName(), actualNode.nodeName()),
                    () -> assertEquals(expectedNode.componentType(), actualNode.componentType()),
                    () -> assertEquals(0, expectedNode.positionX().compareTo(actualNode.positionX())),
                    () -> assertEquals(0, expectedNode.positionY().compareTo(actualNode.positionY())),
                    () -> assertEquals(expectedNode.properties(), actualNode.properties()),
                    () -> assertEquals(expectedNode.id(), actualNode.id())
            );
        }
    }

    private List<String> edgeKeys(List<ProjectEdgeResDTO.EdgeInfoResDTO> edges) {
        return edges.stream()
                .map(edge -> edge.id() + ":" + edge.sourceNodeId() + "->" + edge.targetNodeId())
                .sorted()
                .toList();
    }

    private Member member() {
        Member member = Member.builder().email(UUID.randomUUID() + "@test.example").password("test-only")
                .nickname("tester").role(Role.ROLE_USER).isActive(true).build();
        entityManager.persist(member);
        return member;
    }

    private Project project(Member owner, String title, int day) {
        Project project = Project.builder().member(owner).title(title).description("Description")
                .status(ProjectStatus.DRAFT).build();
        entityManager.persist(project);
        ReflectionTestUtils.setField(project, "createdAt", LocalDateTime.of(2026, 9, day, 12, 0));
        return project;
    }

    private void membership(Project project, Member member, ProjectCollaboratorRole role) {
        entityManager.persist(ProjectCollaborator.builder().project(project).member(member).role(role).build());
    }

    private ProjectNode node(Project project, String id, ComponentType type) {
        ProjectNode node = ProjectNode.builder().project(project).nodeId(id).nodeName(id).componentType(type)
                .positionX(BigDecimal.ZERO).positionY(BigDecimal.ONE).properties(Map.of()).build();
        entityManager.persist(node);
        return node;
    }

    private record Fixture(Long projectId, Long ownerId, Long editorId, Long viewerId, Long strangerId) {
    }
}
