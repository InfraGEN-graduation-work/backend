package com.infragen.infragen.global.auth.websocket;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationOperationReqDTO;
import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.dto.request.ProjectNodeReqDTO;
import com.infragen.infragen.domain.project.dto.request.ProjectReqDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.global.enums.ComponentType;
import com.infragen.infragen.global.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.UserDestinationResolver;
import org.springframework.messaging.simp.user.UserDestinationResult;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 기존 STOMP 연결을 유지한 채 HTTP 권한 변경이 수신·송신에 반영되는지 검증한다. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompAccessRevocationIntegrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse(
            "mysql:8.4.6@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9")
            .asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("infragen_access_revocation_test")
            .withUsername("infragen_test").withPassword("infragen_test_password");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379).withCommand("redis-server", "--requirepass", "test-redis-password");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.docker.compose.enabled", () -> false);
        registry.add("collaboration.compaction.enabled", () -> false);
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> "test-redis-password");
        registry.add("jwt.secret", () -> "test-jwt-secret-test-jwt-secret-test-jwt-secret-1234567890");
        registry.add("kakao.client-id", () -> "test-kakao-client-id");
        registry.add("kakao.client-secret", () -> "test-kakao-client-secret");
        registry.add("kakao.redirect-uri", () -> "http://localhost/test-callback");
        registry.add("kakao.authorization-uri", () -> "http://localhost/kakao/authorize");
        registry.add("kakao.token-uri", () -> "http://localhost/kakao/token");
        registry.add("kakao.user-info-uri", () -> "http://localhost/kakao/user-info");
    }

    @LocalServerPort
    private int serverPort;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectNodeRepository nodeRepository;
    @Autowired
    private ProjectCollaboratorRepository collaboratorRepository;
    @Autowired
    private ProjectCollaborationOperationRepository operationRepository;
    @Autowired
    private SimpleBrokerMessageHandler broker;
    @Autowired
    private UserDestinationResolver userDestinationResolver;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtUtil jwtUtil;

    private final List<Client> clients = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Client client : clients) {
            if (client.session().isConnected()) {
                client.session().disconnect();
            }
            client.transport().stop();
        }
        for (String table : List.of("generated_file", "project_history", "project_collaboration_checkpoint_failure",
                "project_collaboration_snapshot", "project_collaboration_operation", "project_collaboration_state",
                "project_collaborator", "project_edge", "project_node", "project", "member")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @ParameterizedTest
    @EnumSource(ProjectCollaboratorRole.class)
    @DisplayName("삭제된 collaborator의 모든 연결에서 operation·resync를 차단하고 다른 프로젝트 수신은 유지한다")
    void deleteCollaborator_ExistingSubscriptions_StopOnlyRevokedProject(ProjectCollaboratorRole role) throws Exception {
        // given
        Member owner = member();
        Member revoked = member();
        Member viewer = member();
        Project target = project(owner);
        Project other = project(owner);
        collaborate(target, revoked, role);
        collaborate(target, viewer, ProjectCollaboratorRole.VIEWER);
        collaborate(other, revoked, ProjectCollaboratorRole.VIEWER);
        Client ownerClient = connect(owner);
        Client first = connect(revoked);
        Client second = connect(revoked);
        Client remaining = connect(viewer);
        BlockingQueue<JsonNode> ownerOperations = subscribe(ownerClient, topic(target, "operations"));
        BlockingQueue<JsonNode> firstOperations = subscribe(first, topic(target, "operations"));
        BlockingQueue<JsonNode> secondOperations = subscribe(second, topic(target, "operations"));
        BlockingQueue<JsonNode> remainingOperations = subscribe(remaining, topic(target, "operations"));
        BlockingQueue<JsonNode> firstResync = subscribe(first, topic(target, "resync"));
        BlockingQueue<JsonNode> secondResync = subscribe(second, topic(target, "resync"));
        BlockingQueue<JsonNode> remainingResync = subscribe(remaining, topic(target, "resync"));
        BlockingQueue<JsonNode> otherFirst = subscribe(first, topic(other, "resync"));
        BlockingQueue<JsonNode> otherSecond = subscribe(second, topic(other, "resync"));
        send(ownerClient, target, operation("before-revocation"));
        received(ownerOperations);
        received(firstOperations);
        received(secondOperations);
        received(remainingOperations);

        // when
        http(owner).delete().uri("/api/v1/projects/{projectId}/collaborators/{memberId}", target.getId(), revoked.getId())
                .retrieve().toBodilessEntity();
        CollaborationOperationReqDTO.Operation change = operation("after-revocation");
        send(ownerClient, target, change);
        JsonNode ownerResult = received(ownerOperations);
        JsonNode viewerResult = received(remainingOperations);
        replace(owner, target, ownerResult.get("serverVersion").asLong(), "new-target");
        JsonNode viewerResync = received(remainingResync);
        replace(owner, other, 0L, "other-project");
        JsonNode firstOtherResult = received(otherFirst);
        JsonNode secondOtherResult = received(otherSecond);

        // then
        assertEquals(change.operationId(), viewerResult.get("operationId").asString());
        assertEquals("new-target", viewerResync.get("project").get("nodes").get(0).get("nodeName").asString());
        assertEquals("other-project", firstOtherResult.get("project").get("nodes").get(0).get("nodeName").asString());
        assertEquals(firstOtherResult, secondOtherResult);
        for (BlockingQueue<JsonNode> queue : List.of(firstOperations, secondOperations, firstResync, secondResync)) {
            assertNull(queue.poll(500, TimeUnit.MILLISECONDS));
        }
        assertTrue(first.session().isConnected());
        assertTrue(second.session().isConnected());
    }

    @Test
    @DisplayName("EDITOR가 VIEWER로 바뀌면 쓰기는 거부하고 같은 연결의 operation·resync 읽기는 유지한다")
    void changeRole_ToViewer_RejectsSendAndKeepsReceiving() throws Exception {
        // given
        Member owner = member();
        Member editor = member();
        Project project = project(owner);
        collaborate(project, editor, ProjectCollaboratorRole.EDITOR);
        Client ownerClient = connect(owner);
        Client editorClient = connect(editor);
        BlockingQueue<JsonNode> ownerOperations = subscribe(ownerClient, topic(project, "operations"));
        BlockingQueue<JsonNode> editorOperations = subscribe(editorClient, topic(project, "operations"));
        BlockingQueue<JsonNode> editorResync = subscribe(editorClient, topic(project, "resync"));
        BlockingQueue<JsonNode> errors = subscribe(editorClient, "/user/queue/projects/" + project.getId() + "/operation-results");
        CollaborationOperationReqDTO.Operation rejected = operation("forbidden-name");

        // when
        http(owner).patch().uri("/api/v1/projects/{projectId}/collaborators/{memberId}", project.getId(), editor.getId())
                .body(Map.of("role", "VIEWER")).retrieve().toBodilessEntity();
        send(editorClient, project, rejected);
        JsonNode error = received(errors);
        CollaborationOperationReqDTO.Operation allowed = operation("owner-name");
        send(ownerClient, project, allowed);
        JsonNode ownerResult = received(ownerOperations);
        JsonNode editorResult = received(editorOperations);
        replace(owner, project, ownerResult.get("serverVersion").asLong(), "owner-replacement");
        JsonNode resync = received(editorResync);

        // then
        assertEquals("PROJECT403_1", error.get("code").asString());
        assertTrue(operationRepository.findByProjectIdAndOperationId(project.getId(), rejected.operationId()).isEmpty());
        assertEquals(allowed.operationId(), editorResult.get("operationId").asString());
        assertEquals("owner-replacement", resync.get("project").get("nodes").get(0).get("nodeName").asString());
        assertTrue(editorClient.session().isConnected());
    }

    @Test
    @DisplayName("삭제된 collaborator의 기존 SEND는 오류 결과로 거부되고 graph·operation을 변경하지 않는다")
    void deleteCollaborator_ExistingSend_IsRejected() throws Exception {
        // given
        Member owner = member();
        Member editor = member();
        Project project = project(owner);
        collaborate(project, editor, ProjectCollaboratorRole.EDITOR);
        Client client = connect(editor);
        BlockingQueue<JsonNode> errors = subscribe(client, "/user/queue/projects/" + project.getId() + "/operation-results");
        CollaborationOperationReqDTO.Operation rejected = operation("forbidden-name");

        // when
        http(owner).delete().uri("/api/v1/projects/{projectId}/collaborators/{memberId}", project.getId(), editor.getId())
                .retrieve().toBodilessEntity();
        send(client, project, rejected);
        JsonNode error = received(errors);

        // then
        assertEquals("PROJECT403_1", error.get("code").asString());
        assertTrue(operationRepository.findByProjectIdAndOperationId(project.getId(), rejected.operationId()).isEmpty());
        assertEquals("initial-name", nodeRepository.findByProjectIdAndNodeId(project.getId(), "node-1").orElseThrow().getNodeName());
        assertTrue(client.session().isConnected());
    }

    @Test
    @DisplayName("프로젝트 삭제 뒤 늦게 도착한 방송도 기존 owner·collaborator 구독에 전달하지 않는다")
    void deleteProject_LateBroadcast_IsBlocked() throws Exception {
        // given
        Member owner = member();
        Member viewer = member();
        Project target = project(owner);
        Project other = project(owner);
        collaborate(target, viewer, ProjectCollaboratorRole.VIEWER);
        collaborate(other, viewer, ProjectCollaboratorRole.VIEWER);
        Client ownerClient = connect(owner);
        Client viewerClient = connect(viewer);
        BlockingQueue<JsonNode> ownerOperations = subscribe(ownerClient, topic(target, "operations"));
        BlockingQueue<JsonNode> viewerOperations = subscribe(viewerClient, topic(target, "operations"));
        BlockingQueue<JsonNode> ownerResync = subscribe(ownerClient, topic(target, "resync"));
        BlockingQueue<JsonNode> viewerResync = subscribe(viewerClient, topic(target, "resync"));
        BlockingQueue<JsonNode> otherResync = subscribe(viewerClient, topic(other, "resync"));

        // when
        http(owner).delete().uri("/api/v1/projects/{projectId}", target.getId()).retrieve().toBodilessEntity();
        messagingTemplate.convertAndSend(topic(target, "operations"), (Object) Map.of("operationId", "late-operation"));
        messagingTemplate.convertAndSend(topic(target, "resync"), (Object) Map.of("serverVersion", 1));
        replace(owner, other, 0L, "still-readable");
        JsonNode otherResult = received(otherResync);

        // then
        assertEquals("still-readable", otherResult.get("project").get("nodes").get(0).get("nodeName").asString());
        for (BlockingQueue<JsonNode> queue : List.of(ownerOperations, viewerOperations, ownerResync, viewerResync)) {
            assertNull(queue.poll(500, TimeUnit.MILLISECONDS));
        }
    }

    private BlockingQueue<JsonNode> subscribe(Client client, String destination) {
        String id = UUID.randomUUID().toString();
        BlockingQueue<JsonNode> queue = new LinkedBlockingQueue<>();
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setId(id);
        client.session().subscribe(headers, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders stompHeaders) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders stompHeaders, Object payload) {
                queue.add((JsonNode) payload);
            }
        });
        // 구독 요청 발행이 아니라 실제 broker 등록을 확인한 뒤 테스트를 진행한다.
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            if (!destination.startsWith("/user/")) {
                return registered(destination, id);
            }
            String userDestination = "/user/" + client.member().getId() + destination.substring("/user".length());
            UserDestinationResult resolved = userDestinationResolver.resolveDestination(message(userDestination));
            return resolved != null && resolved.getTargetDestinations().stream().anyMatch(target -> registered(target, id));
        });
        return queue;
    }

    private boolean registered(String destination, String subscriptionId) {
        return broker.getSubscriptionRegistry().findSubscriptions(message(destination)).values().stream()
                .anyMatch(ids -> ids.contains(subscriptionId));
    }

    private Message<byte[]> message(String destination) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private JsonNode received(BlockingQueue<JsonNode> queue) throws InterruptedException {
        JsonNode message = queue.poll(5, TimeUnit.SECONDS);
        assertNotNull(message, "STOMP 메시지가 제한 시간 내 도착해야 한다");
        return message;
    }

    private Client connect(Member member) throws Exception {
        WebSocketStompClient transport = new WebSocketStompClient(new StandardWebSocketClient());
        transport.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + jwtUtil.createAccessToken(member.getId(), member.getRole()));
        StompSession session = transport.connectAsync("ws://localhost:" + serverPort + "/ws/collaboration",
                new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        Client client = new Client(member, transport, session);
        clients.add(client);
        return client;
    }

    private void send(Client client, Project project, CollaborationOperationReqDTO.Operation operation) {
        client.session().send("/app/projects/" + project.getId() + "/operations", operation);
    }

    private CollaborationOperationReqDTO.Operation operation(String name) {
        return new CollaborationOperationReqDTO.Operation(UUID.randomUUID().toString(), "client-1", 0L,
                CollaborationOperationType.UPDATE_NODE_NAME, "node-1", Map.of("value", name));
    }

    private void replace(Member owner, Project project, long baseVersion, String name) {
        http(owner).put().uri("/api/v1/projects/{projectId}", project.getId())
                .body(new ProjectReqDTO.UpdateProjectReqDTO("project", null,
                        List.of(new ProjectNodeReqDTO.NodeInfoReqDTO("node-1", name, "MYSQL",
                                BigDecimal.ZERO, BigDecimal.ZERO, Map.of())), List.of(), baseVersion))
                .retrieve().toBodilessEntity();
    }

    private RestClient http(Member member) {
        return RestClient.builder().baseUrl("http://localhost:" + serverPort)
                .defaultHeaders(headers -> headers.setBearerAuth(jwtUtil.createAccessToken(member.getId(), member.getRole())))
                .build();
    }

    private String topic(Project project, String type) {
        return "/topic/projects/" + project.getId() + "/" + type;
    }

    private Member member() {
        return memberRepository.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@infragen.test")
                .password("encoded-test-password").nickname("revocation-test").role(Role.ROLE_USER).isActive(true).build());
    }

    private Project project(Member owner) {
        Project project = projectRepository.saveAndFlush(Project.builder().member(owner).title("project").status(ProjectStatus.DRAFT).build());
        nodeRepository.saveAndFlush(ProjectNode.builder().project(project).nodeId("node-1").nodeName("initial-name")
                .componentType(ComponentType.MYSQL).positionX(BigDecimal.ZERO).positionY(BigDecimal.ZERO).properties(Map.of()).build());
        return project;
    }

    private void collaborate(Project project, Member member, ProjectCollaboratorRole role) {
        collaboratorRepository.saveAndFlush(ProjectCollaborator.builder().project(project).member(member).role(role).build());
    }

    private record Client(Member member, WebSocketStompClient transport, StompSession session) {
    }
}
