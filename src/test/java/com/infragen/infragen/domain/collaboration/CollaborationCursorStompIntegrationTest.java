package com.infragen.infragen.domain.collaboration;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.global.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 실제 STOMP 연결로 커서 전송·방송·연결 종료 숨김·권한 회수를 검증한다. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CollaborationCursorStompIntegrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse(
            "mysql:8.4.6@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9")
            .asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("infragen_cursor_test")
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
    private ProjectCollaboratorRepository collaboratorRepository;
    @Autowired
    private SimpleBrokerMessageHandler broker;
    @Autowired
    private UserDestinationResolver userDestinationResolver;
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

    @Test
    @DisplayName("VIEWER가 보낸 커서를 같은 프로젝트 구독자만 서버 식별자와 함께 받는다")
    void sendCursor_Viewer_DeliversToSameProjectOnly() throws Exception {
        // given
        Member owner = member();
        Member viewer = member();
        Project target = project(owner);
        Project other = project(owner);
        collaborate(target, viewer, ProjectCollaboratorRole.VIEWER);
        Client ownerClient = connect(owner);
        Client viewerClient = connect(viewer);
        BlockingQueue<JsonNode> ownerTarget = subscribe(ownerClient, topic(target));
        BlockingQueue<JsonNode> ownerOther = subscribe(ownerClient, topic(other));

        // when
        sendCursor(viewerClient, target, Map.of("visible", true, "x", 10.5, "y", 20.25));
        JsonNode cursor = received(ownerTarget);

        // then
        assertAll(
                () -> assertEquals(viewer.getId(), cursor.get("actorMemberId").asLong()),
                () -> assertTrue(cursor.get("visible").asBoolean()),
                () -> assertEquals(10.5, cursor.get("x").asDouble()),
                () -> assertEquals(20.25, cursor.get("y").asDouble()),
                () -> assertFalse(cursor.get("cursorId").asString().isBlank()),
                () -> assertNull(ownerOther.poll(500, TimeUnit.MILLISECONDS))
        );
    }

    @Test
    @DisplayName("숨김 메시지는 좌표 없이 보내도 x·y가 null인 visible=false로 전달된다")
    void sendCursor_HiddenWithoutCoordinates_DeliversNullCoordinates() throws Exception {
        // given
        Member owner = member();
        Member viewer = member();
        Project project = project(owner);
        collaborate(project, viewer, ProjectCollaboratorRole.VIEWER);
        Client ownerClient = connect(owner);
        Client viewerClient = connect(viewer);
        BlockingQueue<JsonNode> ownerQueue = subscribe(ownerClient, topic(project));

        // when
        sendCursor(viewerClient, project, Map.of("visible", false));
        JsonNode hidden = received(ownerQueue);

        // then
        assertAll(
                () -> assertFalse(hidden.get("visible").asBoolean()),
                () -> assertTrue(hidden.has("x") && hidden.get("x").isNull()),
                () -> assertTrue(hidden.has("y") && hidden.get("y").isNull())
        );
    }

    @Test
    @DisplayName("연결이 끝나면 그 연결이 커서를 보여 주던 모든 프로젝트에 같은 cursorId로 숨김이 전달된다")
    void disconnect_MultipleProjects_HidesCursorInEachProject() throws Exception {
        // given
        Member owner = member();
        Member viewer = member();
        Project first = project(owner);
        Project second = project(owner);
        collaborate(first, viewer, ProjectCollaboratorRole.VIEWER);
        collaborate(second, viewer, ProjectCollaboratorRole.VIEWER);
        Client ownerClient = connect(owner);
        Client viewerClient = connect(viewer);
        BlockingQueue<JsonNode> firstQueue = subscribe(ownerClient, topic(first));
        BlockingQueue<JsonNode> secondQueue = subscribe(ownerClient, topic(second));
        sendCursor(viewerClient, first, Map.of("visible", true, "x", 1.0, "y", 2.0));
        sendCursor(viewerClient, second, Map.of("visible", true, "x", 3.0, "y", 4.0));
        String cursorId = received(firstQueue).get("cursorId").asString();
        received(secondQueue);

        // when
        viewerClient.session().disconnect();
        JsonNode firstHidden = received(firstQueue);
        JsonNode secondHidden = received(secondQueue);

        // then
        assertAll(
                () -> assertFalse(firstHidden.get("visible").asBoolean()),
                () -> assertFalse(secondHidden.get("visible").asBoolean()),
                () -> assertEquals(cursorId, firstHidden.get("cursorId").asString()),
                () -> assertEquals(cursorId, secondHidden.get("cursorId").asString()),
                () -> assertEquals(viewer.getId(), firstHidden.get("actorMemberId").asLong()),
                () -> assertTrue(firstHidden.get("x").isNull()),
                () -> assertNull(firstQueue.poll(500, TimeUnit.MILLISECONDS)),
                () -> assertNull(secondQueue.poll(500, TimeUnit.MILLISECONDS))
        );
    }

    @Test
    @DisplayName("같은 회원의 다른 탭은 서로 다른 cursorId를 받고, 한 탭이 끝나도 다른 탭 커서는 숨기지 않는다")
    void disconnect_OneOfTwoTabs_PreservesOtherTabCursor() throws Exception {
        // given
        Member owner = member();
        Member viewer = member();
        Project project = project(owner);
        collaborate(project, viewer, ProjectCollaboratorRole.VIEWER);
        Client ownerClient = connect(owner);
        Client firstTab = connect(viewer);
        Client secondTab = connect(viewer);
        BlockingQueue<JsonNode> ownerQueue = subscribe(ownerClient, topic(project));
        sendCursor(firstTab, project, Map.of("visible", true, "x", 1.0, "y", 1.0));
        String firstCursorId = received(ownerQueue).get("cursorId").asString();
        sendCursor(secondTab, project, Map.of("visible", true, "x", 2.0, "y", 2.0));
        String secondCursorId = received(ownerQueue).get("cursorId").asString();

        // when
        firstTab.session().disconnect();
        JsonNode hidden = received(ownerQueue);

        // then
        assertAll(
                () -> assertNotEquals(firstCursorId, secondCursorId),
                () -> assertEquals(firstCursorId, hidden.get("cursorId").asString()),
                () -> assertNull(ownerQueue.poll(500, TimeUnit.MILLISECONDS))
        );
    }

    @Test
    @DisplayName("범위를 벗어난 좌표는 요청자에게만 COLLAB400_3을 돌려주고 방송하지 않는다")
    void sendCursor_OutOfRangeCoordinate_RejectsOnlyToRequester() throws Exception {
        // given
        Member owner = member();
        Member viewer = member();
        Project project = project(owner);
        collaborate(project, viewer, ProjectCollaboratorRole.VIEWER);
        Client ownerClient = connect(owner);
        Client viewerClient = connect(viewer);
        BlockingQueue<JsonNode> ownerQueue = subscribe(ownerClient, topic(project));
        BlockingQueue<JsonNode> viewerErrors = subscribe(viewerClient, errors(project));

        // when
        sendCursor(viewerClient, project, Map.of("visible", true, "x", 1.0e10, "y", 0.0));
        JsonNode error = received(viewerErrors);

        // then
        assertAll(
                () -> assertEquals("COLLAB400_3", error.get("code").asString()),
                () -> assertNull(ownerQueue.poll(500, TimeUnit.MILLISECONDS))
        );
    }

    @Test
    @DisplayName("삭제된 collaborator의 기존 연결이 보낸 커서는 PROJECT403_1로 거부되고 방송되지 않는다")
    void sendCursor_RevokedCollaborator_IsRejected() throws Exception {
        // given
        Member owner = member();
        Member viewer = member();
        Project project = project(owner);
        collaborate(project, viewer, ProjectCollaboratorRole.VIEWER);
        Client ownerClient = connect(owner);
        Client viewerClient = connect(viewer);
        BlockingQueue<JsonNode> ownerQueue = subscribe(ownerClient, topic(project));
        BlockingQueue<JsonNode> viewerErrors = subscribe(viewerClient, errors(project));

        // when
        http(owner).delete().uri("/api/v1/projects/{projectId}/collaborators/{memberId}", project.getId(), viewer.getId())
                .retrieve().toBodilessEntity();
        sendCursor(viewerClient, project, Map.of("visible", true, "x", 1.0, "y", 1.0));
        JsonNode error = received(viewerErrors);

        // then
        assertAll(
                () -> assertEquals("PROJECT403_1", error.get("code").asString()),
                () -> assertNull(ownerQueue.poll(500, TimeUnit.MILLISECONDS))
        );
    }

    @Test
    @DisplayName("삭제된 collaborator의 기존 cursors 구독에는 커서가 전달되지 않고 다른 프로젝트 구독은 유지된다")
    void deleteCollaborator_ExistingCursorSubscription_StopsOnlyRevokedProject() throws Exception {
        // given
        Member owner = member();
        Member revoked = member();
        Member remaining = member();
        Project target = project(owner);
        Project other = project(owner);
        collaborate(target, revoked, ProjectCollaboratorRole.VIEWER);
        collaborate(target, remaining, ProjectCollaboratorRole.VIEWER);
        collaborate(other, revoked, ProjectCollaboratorRole.VIEWER);
        Client ownerClient = connect(owner);
        Client revokedClient = connect(revoked);
        Client remainingClient = connect(remaining);
        BlockingQueue<JsonNode> revokedTarget = subscribe(revokedClient, topic(target));
        BlockingQueue<JsonNode> revokedOther = subscribe(revokedClient, topic(other));
        BlockingQueue<JsonNode> remainingTarget = subscribe(remainingClient, topic(target));

        // when
        http(owner).delete().uri("/api/v1/projects/{projectId}/collaborators/{memberId}", target.getId(), revoked.getId())
                .retrieve().toBodilessEntity();
        sendCursor(ownerClient, target, Map.of("visible", true, "x", 5.0, "y", 6.0));
        sendCursor(ownerClient, other, Map.of("visible", true, "x", 7.0, "y", 8.0));
        JsonNode remainingResult = received(remainingTarget);
        JsonNode revokedOtherResult = received(revokedOther);

        // then
        assertAll(
                () -> assertEquals(5.0, remainingResult.get("x").asDouble()),
                () -> assertEquals(7.0, revokedOtherResult.get("x").asDouble()),
                () -> assertNull(revokedTarget.poll(500, TimeUnit.MILLISECONDS))
        );
    }

    private void sendCursor(Client client, Project project, Map<String, Object> cursor) {
        client.session().send("/app/projects/" + project.getId() + "/cursors", cursor);
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

    private RestClient http(Member member) {
        return RestClient.builder().baseUrl("http://localhost:" + serverPort)
                .defaultHeaders(headers -> headers.setBearerAuth(jwtUtil.createAccessToken(member.getId(), member.getRole())))
                .build();
    }

    private String topic(Project project) {
        return "/topic/projects/" + project.getId() + "/cursors";
    }

    private String errors(Project project) {
        return "/user/queue/projects/" + project.getId() + "/operation-results";
    }

    private Member member() {
        return memberRepository.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@infragen.test")
                .password("encoded-test-password").nickname("cursor-test").role(Role.ROLE_USER).isActive(true).build());
    }

    private Project project(Member owner) {
        return projectRepository.saveAndFlush(Project.builder().member(owner).title("project").status(ProjectStatus.DRAFT).build());
    }

    private void collaborate(Project project, Member member, ProjectCollaboratorRole role) {
        collaboratorRepository.saveAndFlush(ProjectCollaborator.builder().project(project).member(member).role(role).build());
    }

    private record Client(Member member, WebSocketStompClient transport, StompSession session) {
    }
}
