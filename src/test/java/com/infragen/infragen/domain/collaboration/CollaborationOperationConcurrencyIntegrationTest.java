package com.infragen.infragen.domain.collaboration;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationOperationReqDTO;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationSnapshot;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationState;
import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationSnapshotRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationStateRepository;
import com.infragen.infragen.domain.collaboration.service.command.CollaborationOperationCommandService;
import com.infragen.infragen.domain.collaboration.service.command.CollaborationOperationCompactionService;
import com.infragen.infragen.domain.collaboration.service.query.CollaborationSnapshotQueryService;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationSnapshotResDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationCheckpointFailureRepository;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.dto.request.ProjectEdgeReqDTO;
import com.infragen.infragen.domain.project.dto.request.ProjectNodeReqDTO;
import com.infragen.infragen.domain.project.dto.request.ProjectReqDTO;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.service.command.ProjectCommandService;
import com.infragen.infragen.global.enums.ComponentType;
import com.infragen.infragen.global.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CollaborationOperationConcurrencyIntegrationTest {
    private static final String MYSQL_IMAGE =
            "mysql:8.4.6@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9";
    private static final String REDIS_PASSWORD = "test-redis-password";

    @LocalServerPort
    private int serverPort;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql")
    )
            .withDatabaseName("infragen_collaboration_test")
            .withUsername("infragen_test")
            .withPassword("infragen_test_password");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
        registry.add("jwt.secret", () ->
                "test-jwt-secret-test-jwt-secret-test-jwt-secret-1234567890");
        registry.add("kakao.client-id", () -> "test-kakao-client-id");
        registry.add("kakao.client-secret", () -> "test-kakao-client-secret");
        registry.add("kakao.redirect-uri", () -> "http://localhost/test-callback");
        registry.add("kakao.authorization-uri", () -> "http://localhost/kakao/authorize");
        registry.add("kakao.token-uri", () -> "http://localhost/kakao/token");
        registry.add("kakao.user-info-uri", () -> "http://localhost/kakao/user-info");
    }

    @Autowired
    private CollaborationOperationCommandService operationCommandService;

    @Autowired
    private CollaborationOperationCompactionService compactionService;

    @Autowired
    private ProjectCommandService projectCommandService;

    @Autowired
    private CollaborationSnapshotQueryService snapshotQueryService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectNodeRepository projectNodeRepository;

    @Autowired
    private ProjectCollaborationStateRepository stateRepository;

    @Autowired
    private ProjectCollaborationOperationRepository operationRepository;

    @Autowired
    private ProjectCollaborationSnapshotRepository snapshotRepository;

    @Autowired
    private ProjectCollaborationCheckpointFailureRepository checkpointFailureRepository;

    @Autowired
    private ProjectCollaboratorRepository collaboratorRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @AfterEach
    void tearDown() {
        checkpointFailureRepository.deleteAllInBatch();
        operationRepository.deleteAllInBatch();
        snapshotRepository.deleteAllInBatch();
        stateRepository.deleteAllInBatch();
        collaboratorRepository.deleteAllInBatch();
        projectNodeRepository.deleteAllInBatch();
        projectRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("동일 operationId 동시 요청을 한 번만 저장한다")
    void recordOperation_withConcurrentSameOperationId_persistsOnlyOnce() throws Exception {
        // given
        Member owner = saveMember();
        Project project = saveProject(owner);
        saveNode(project);
        String operationId = UUID.randomUUID().toString();
        CollaborationOperationReqDTO.Operation operation = operation(operationId);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Optional<?>> first = submit(executor, startGate, project, owner, operation);
            Future<Optional<?>> second = submit(executor, startGate, project, owner, operation);

            // when
            startGate.countDown();
            Optional<?> firstResult = first.get(20, TimeUnit.SECONDS);
            Optional<?> secondResult = second.get(20, TimeUnit.SECONDS);

            // then
            assertTrue(firstResult.isPresent() ^ secondResult.isPresent());
            assertEquals(1, operationRepository.findAllByProjectIdOrderByServerVersionAsc(project.getId()).size());
            ProjectCollaborationOperation savedOperation = operationRepository
                    .findAllByProjectIdOrderByServerVersionAsc(project.getId())
                    .getFirst();
            assertEquals(1L, savedOperation.getServerVersion());
            assertEquals("database", projectNodeRepository
                    .findByProjectIdAndNodeId(project.getId(), "node-1")
                    .orElseThrow()
                    .getNodeName());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("compact 이후 오래된 client가 snapshot과 남은 operation으로 복구된다")
    void compact_withOldAfterVersion_restoresSnapshotAndReplaysRemainingOperations() {
        // given
        Member owner = saveMember();
        Project project = saveProject(owner);
        saveNode(project);
        ProjectCollaborationState state = new ProjectCollaborationState(project);
        state.advanceServerVersion();
        state.advanceServerVersion();
        state.advanceServerVersion();
        stateRepository.saveAndFlush(state);

        snapshotRepository.saveAndFlush(ProjectCollaborationSnapshot.builder()
                .project(project)
                .updatedBy(owner)
                .serverVersion(2L)
                .graphPayload(Map.of(
                        "projectId", project.getId(),
                        "title", project.getTitle(),
                        "status", "DRAFT",
                        "nodes", List.of(),
                        "edges", List.of()
                ))
                .build());
        operationRepository.saveAllAndFlush(List.of(
                operation(project, owner, 1L),
                operation(project, owner, 2L),
                operation(project, owner, 3L)
        ));

        // when
        int deletedCount = compactionService.compact(project.getId());
        CollaborationSnapshotResDTO.SnapshotResDTO result = snapshotQueryService.getSnapshot(
                project.getId(),
                owner.getId(),
                1L
        );

        // then
        assertEquals(2, deletedCount);
        assertEquals(1, operationRepository
                .findAllByProjectIdOrderByServerVersionAsc(project.getId())
                .size());
        assertEquals(2L, result.graphVersion());
        assertEquals(3L, result.serverVersion());
        assertEquals(1, result.operations().size());
        assertEquals(3L, result.operations().get(0).serverVersion());
    }

    @Test
    @DisplayName("PUT과 operation이 동시에 실행되어도 operation 변경을 덮어쓰지 않는다")
    void updateProject_withConcurrentOperation_doesNotOverwriteOperationMaterialization() throws Exception {
        // given
        Member owner = saveMember();
        Project project = saveProject(owner);
        saveNode(project);
        CollaborationOperationReqDTO.Operation operation = operation(UUID.randomUUID().toString());
        ProjectReqDTO.UpdateProjectReqDTO putRequest = new ProjectReqDTO.UpdateProjectReqDTO(
                "put-title",
                "put-description",
                List.of(new ProjectNodeReqDTO.NodeInfoReqDTO(
                        "node-1",
                        "put-name",
                        ComponentType.MYSQL.name(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        Map.of()
                )),
                List.of(new ProjectEdgeReqDTO.EdgeInfoReqDTO("node-1", "node-1")),
                0L
        );
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Throwable> putResult = executor.submit(() -> {
                startGate.await();
                try {
                    projectCommandService.updateProject(project.getId(), putRequest, owner.getId());
                    return null;
                } catch (Throwable exception) {
                    return exception;
                }
            });
            Future<Optional<?>> operationResult = submit(executor, startGate, project, owner, operation);

            // when
            startGate.countDown();
            Throwable putException = putResult.get(20, TimeUnit.SECONDS);
            operationResult.get(20, TimeUnit.SECONDS);

            // then
            assertTrue(
                    putException == null
                            || putException instanceof CollaborationException
            );
            assertEquals(
                    "database",
                    projectNodeRepository.findByProjectIdAndNodeId(project.getId(), "node-1")
                            .orElseThrow()
                            .getNodeName()
            );
            assertTrue(stateRepository.findByProjectId(project.getId()).orElseThrow().getServerVersion() >= 1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("두 WebSocket client가 operation broadcast와 PUT resync를 수신한다")
    void websocket_twoClients_receiveOperationAndPutResync() throws Exception {
        // given
        Member owner = saveMember();
        Member editor = saveMember();
        Project project = saveProject(owner);
        saveNode(project);
        collaboratorRepository.saveAndFlush(ProjectCollaborator.builder()
                .project(project)
                .member(editor)
                .role(ProjectCollaboratorRole.EDITOR)
                .build());

        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        StompSession ownerSession = connect(client, owner);
        StompSession editorSession = connect(client, editor);
        BlockingQueue<CollaborationOperationResDTO.BroadcastOperationResDTO> ownerOperations = new LinkedBlockingQueue<>();
        BlockingQueue<CollaborationOperationResDTO.BroadcastOperationResDTO> editorOperations = new LinkedBlockingQueue<>();
        BlockingQueue<CollaborationSnapshotResDTO.SnapshotResDTO> editorResyncs = new LinkedBlockingQueue<>();
        String operationDestination = "/topic/projects/" + project.getId() + "/operations";
        ownerSession.subscribe(operationDestination, handler(CollaborationOperationResDTO.BroadcastOperationResDTO.class, ownerOperations));
        editorSession.subscribe(operationDestination, handler(CollaborationOperationResDTO.BroadcastOperationResDTO.class, editorOperations));
        editorSession.subscribe(
                "/topic/projects/" + project.getId() + "/resync",
                handler(CollaborationSnapshotResDTO.SnapshotResDTO.class, editorResyncs)
        );

        // when
        ownerSession.send(
                "/app/projects/" + project.getId() + "/operations",
                operation(UUID.randomUUID().toString())
        );
        CollaborationOperationResDTO.BroadcastOperationResDTO ownerOperation =
                ownerOperations.poll(10, TimeUnit.SECONDS);
        CollaborationOperationResDTO.BroadcastOperationResDTO editorOperation =
                editorOperations.poll(10, TimeUnit.SECONDS);
        projectCommandService.updateProject(
                project.getId(),
                updateRequest(ownerOperation.serverVersion(), "put-name"),
                owner.getId()
        );
        CollaborationSnapshotResDTO.SnapshotResDTO resync = editorResyncs.poll(10, TimeUnit.SECONDS);

        // then
        assertEquals("database", ownerOperation.payload().get("value"));
        assertEquals(ownerOperation.serverVersion(), editorOperation.serverVersion());
        assertEquals(ownerOperation.serverVersion() + 1L, resync.graphVersion());
        assertEquals("put-name", resync.project().nodes().get(0).nodeName());

        ownerSession.disconnect();
        editorSession.disconnect();
        client.stop();
    }

    private Future<Optional<?>> submit(
            ExecutorService executor,
            CountDownLatch startGate,
            Project project,
            Member owner,
            CollaborationOperationReqDTO.Operation operation
    ) {
        return executor.submit(() -> {
            startGate.await();
            return operationCommandService.recordOperation(project.getId(), owner.getId(), operation);
        });
    }

    private StompSession connect(WebSocketStompClient client, Member member) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + jwtUtil.createAccessToken(member.getId(), member.getRole()));
        return client.connectAsync(
                        "ws://localhost:" + serverPort + "/ws/collaboration",
                        new WebSocketHttpHeaders(),
                        headers,
                        new StompSessionHandlerAdapter() {
                        }
                )
                .get(10, TimeUnit.SECONDS);
    }

    private <T> StompFrameHandler handler(Class<T> payloadType, BlockingQueue<T> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add(payloadType.cast(payload));
            }
        };
    }

    private ProjectReqDTO.UpdateProjectReqDTO updateRequest(Long baseVersion, String nodeName) {
        return new ProjectReqDTO.UpdateProjectReqDTO(
                "updated-title",
                "updated-description",
                List.of(new ProjectNodeReqDTO.NodeInfoReqDTO(
                        "node-1",
                        nodeName,
                        ComponentType.MYSQL.name(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        Map.of()
                )),
                List.of(),
                baseVersion
        );
    }

    private Member saveMember() {
        return memberRepository.saveAndFlush(Member.builder()
                .email(UUID.randomUUID() + "@infragen.test")
                .password("encoded-password")
                .nickname("collaboration-owner")
                .role(Role.ROLE_USER)
                .isActive(true)
                .build());
    }

    private Project saveProject(Member owner) {
        return projectRepository.saveAndFlush(Project.builder()
                .title("collaboration-project")
                .description("concurrency test project")
                .status(ProjectStatus.DRAFT)
                .member(owner)
                .build());
    }

    private void saveNode(Project project) {
        projectNodeRepository.saveAndFlush(ProjectNode.builder()
                .project(project)
                .componentType(ComponentType.MYSQL)
                .nodeId("node-1")
                .nodeName("mysql")
                .positionX(BigDecimal.ZERO)
                .positionY(BigDecimal.ZERO)
                .properties(Map.of())
                .build());
    }

    private CollaborationOperationReqDTO.Operation operation(String operationId) {
        return new CollaborationOperationReqDTO.Operation(
                operationId,
                "client-1",
                0L,
                CollaborationOperationType.UPDATE_NODE_NAME,
                "node-1",
                Map.of("value", "database")
        );
    }

    private ProjectCollaborationOperation operation(
            Project project,
            Member owner,
            Long serverVersion
    ) {
        return ProjectCollaborationOperation.builder()
                .project(project)
                .actorMember(owner)
                .operationId("stored-op-" + serverVersion)
                .clientId("client-1")
                .baseVersion(serverVersion - 1)
                .serverVersion(serverVersion)
                .operationType(CollaborationOperationType.UPDATE_NODE_NAME)
                .nodeId("node-1")
                .payload(Map.of("value", "database-" + serverVersion))
                .build();
    }
}
