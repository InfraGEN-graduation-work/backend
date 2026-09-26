package com.infragen.infragen.domain.generation;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationCheckpointFailureRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationSnapshotRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationStateRepository;
import com.infragen.infragen.domain.project.entity.GeneratedFile;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.entity.ProjectEdge;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.entity.ProjectHistory;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.repository.GeneratedFileRepository;
import com.infragen.infragen.domain.project.repository.ProjectHistoryRepository;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorInvitationRepository;
import com.infragen.infragen.global.enums.ComponentType;
import com.infragen.infragen.global.auth.CustomUserDetails;
import com.infragen.infragen.global.util.JwtUtil;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Generate API 통합 테스트")
class GenerateApiIntegrationTest {
    private static final String MYSQL_IMAGE =
        "mysql:8.4.6@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9";
    private static final String GENERATE_URL = "/api/v1/projects/{projectId}/generate";
    private static final String REDIS_PASSWORD = "test-redis-password";
    private static final String GUEST_GRAPH_JSON = """
        {
          "title": "guest-project",
          "description": "guest integration test",
          "baseVersion": 0,
          "nodes": [
            {
              "nodeId": "node-1",
              "nodeName": "mysql",
              "componentType": "MYSQL",
              "positionX": 100,
              "positionY": 200,
              "properties": {
                "imageVersion": "mysql:8.0",
                "containerName": "guest-mysql",
                "volumeName": "guest_mysql_data",
                "port": 3306,
                "env": {
                  "databaseName": "guestdb",
                  "username": "guestuser",
                  "userPassword": "guestpass12",
                  "rootPassword": "guestroot12"
                }
              }
            },
            {
              "nodeId": "node-2",
              "nodeName": "app",
              "componentType": "SPRING_BOOT",
              "positionX": 400,
              "positionY": 200,
              "properties": {
                "name": "guest-app",
                "port": 8080,
                "javaVersion": "17",
                "containerName": "guest-app"
              }
            }
          ],
          "edges": [
            { "sourceNodeId": "node-1", "targetNodeId": "node-2" }
          ]
        }
        """;
    private static final String REQUEST_JSON = """
        {
          "deploymentOption": "LOCAL",
          "includeLocalSpec": false,
          "deploymentTarget": null,
          "nodes": [
            {
              "nodeId": "durable-mysql-node",
              "componentType": "MYSQL",
              "positionX": 100,
              "positionY": 200,
              "properties": {
                "imageVersion": "mysql:8.0",
                "containerName": "durable-mysql",
                "volumeName": "durable_mysql_data",
                "port": 3306,
                "env": {
                  "databaseName": "durable_db",
                  "username": "durable_user",
                  "userPassword": "durablepass12",
                  "rootPassword": "durableroot12"
                }
              }
            },
            {
              "nodeId": "durable-app-node",
              "componentType": "SPRING_BOOT",
              "positionX": 400,
              "positionY": 200,
              "properties": {
                "name": "durable-app",
                "port": 8080,
                "javaVersion": "17",
                "containerName": "durable-app"
              }
            }
          ],
          "edges": [
            { "sourceNodeId": "durable-mysql-node", "targetNodeId": "durable-app-node" }
          ]
        }
        """;
    private static final String MYSQL_REDIS_REQUEST_JSON = """
        {
          "deploymentOption": "LOCAL",
          "includeLocalSpec": false,
          "deploymentTarget": null,
          "nodes": [
            {
              "nodeId": "durable-mysql-node",
              "componentType": "MYSQL",
              "positionX": 100,
              "positionY": 200,
              "properties": {
                "imageVersion": "mysql:8.0",
                "containerName": "durable-mysql",
                "volumeName": "durable_mysql_data",
                "port": 3306,
                "env": {
                  "databaseName": "durable_db",
                  "username": "durable_user",
                  "userPassword": "durablepass12",
                  "rootPassword": "durableroot12"
                }
              }
            },
            {
              "nodeId": "durable-redis-node",
              "componentType": "REDIS",
              "positionX": 250,
              "positionY": 200,
              "properties": {
                "imageVersion": "redis:7.4",
                "containerName": "durable-redis",
                "volumeName": "durable_redis_data",
                "port": 6379,
                "password": "durable-redis-password"
              }
            },
            {
              "nodeId": "durable-app-node",
              "componentType": "SPRING_BOOT",
              "positionX": 400,
              "positionY": 200,
              "properties": {
                "name": "durable-app",
                "port": 8080,
                "javaVersion": "17",
                "containerName": "durable-app"
              }
            }
          ],
          "edges": [
            { "sourceNodeId": "durable-mysql-node", "targetNodeId": "durable-app-node" },
            { "sourceNodeId": "durable-redis-node", "targetNodeId": "durable-app-node" }
          ]
        }
        """;

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer(
        DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql")
    )
        .withDatabaseName("infragen_test")
        .withUsername("infragen_test")
        .withPassword("infragen_test_password");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379)
        .withCommand("redis-server", "--requirepass", REDIS_PASSWORD, "--appendonly", "yes");

    // 테스트 컨테이너의 DB URL, 사용자명, 비밀번호를 Spring Boot에 전달
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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectCollaboratorRepository projectCollaboratorRepository;

    @Autowired
    private ProjectCollaboratorInvitationRepository projectCollaboratorInvitationRepository;

    @Autowired
    private ProjectCollaborationCheckpointFailureRepository checkpointFailureRepository;

    @Autowired
    private ProjectCollaborationSnapshotRepository collaborationSnapshotRepository;

    @Autowired
    private ProjectCollaborationOperationRepository collaborationOperationRepository;

    @Autowired
    private ProjectCollaborationStateRepository collaborationStateRepository;

    @Autowired
    private ProjectNodeRepository projectNodeRepository;

    @Autowired
    private ProjectEdgeRepository projectEdgeRepository;

    @Autowired
    private ProjectHistoryRepository projectHistoryRepository;

    @Autowired
    private GeneratedFileRepository generatedFileRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        generatedFileRepository.deleteAllInBatch();
        projectHistoryRepository.deleteAllInBatch();
        projectEdgeRepository.deleteAllInBatch();
        projectNodeRepository.deleteAllInBatch();
        checkpointFailureRepository.deleteAllInBatch();
        collaborationSnapshotRepository.deleteAllInBatch();
        collaborationOperationRepository.deleteAllInBatch();
        collaborationStateRepository.deleteAllInBatch();
        projectCollaboratorInvitationRepository.deleteAllInBatch();
        projectCollaboratorRepository.deleteAllInBatch();
        projectRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("소유 프로젝트 생성 — 파일과 historyId를 반환하고 DB에 저장")
    void generate_OwnedProject_SavesFilesAndReturnsHistoryId() throws Exception {
        // given
        Member owner = saveMember("owner@infragen.test");
        Project project = saveProject(owner, "owned-project");
        saveDurableGraph(project, false);

        // when
        ResultActions result = mockMvc.perform(post(GENERATE_URL, project.getId())
                .with(authenticatedAs(owner)) // 인증된 사용자로 요청
                .contentType(APPLICATION_JSON)
                .content(REQUEST_JSON));

        // then
        result
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.code").value("GENERATION200_1"))
            .andExpect(jsonPath("$.result.historyId").isNumber())
            .andExpect(jsonPath("$.result.files.length()").value(2));

        // then
        List<ProjectHistory> histories = projectHistoryRepository
            .findAllByProjectIdOrderByCreatedAtDesc(project.getId());
        assertEquals(1, histories.size());

        ProjectHistory history = histories.get(0);
        assertEquals("v1", history.getVersionName());
        assertEquals(owner.getId(), history.getActorMemberId());

        List<GeneratedFile> generatedFiles = generatedFileRepository
            .findAllByProjectHistoryId(history.getId());
        assertEquals(2, generatedFiles.size());
        assertTrue(generatedFiles.stream().anyMatch(file ->
            "local/docker-compose.yml".equals(file.getFileName())
                && file.getContent().contains("services:")));
        assertTrue(generatedFiles.stream().anyMatch(file ->
            "local/.env".equals(file.getFileName())
                && file.getContent().contains("localhost")));
        assertTrue(generatedFiles.stream().anyMatch(file ->
            "local/docker-compose.yml".equals(file.getFileName())
                && file.getContent().contains("durable-mysql")));
    }

    @Test
    @DisplayName("실제 JWT와 MySQL에서 이력 역할 경계와 생성자·과거 null을 반환한다")
    void projectHistory_CollaboratorRolesAndLegacyActor_EnforcesApiContract() throws Exception {
        // given
        Member owner = saveMember("history-owner@infragen.test");
        Member editor = saveMember("history-editor@infragen.test");
        Member viewer = saveMember("history-viewer@infragen.test");
        Member outsider = saveMember("history-outsider@infragen.test");
        Project project = saveProject(owner, "history-access-project");
        projectCollaboratorRepository.saveAllAndFlush(List.of(
            ProjectCollaborator.builder().project(project).member(editor)
                .role(ProjectCollaboratorRole.EDITOR).build(),
            ProjectCollaborator.builder().project(project).member(viewer)
                .role(ProjectCollaboratorRole.VIEWER).build()
        ));
        ProjectHistory legacy = projectHistoryRepository.saveAndFlush(ProjectHistory.builder()
            .project(project).versionName("v1").description("old record").build());
        String ownerToken = jwtUtil.createAccessToken(owner.getId(), owner.getRole());
        String editorToken = jwtUtil.createAccessToken(editor.getId(), editor.getRole());
        String viewerToken = jwtUtil.createAccessToken(viewer.getId(), viewer.getRole());
        String outsiderToken = jwtUtil.createAccessToken(outsider.getId(), outsider.getRole());
        String historiesUrl = "/api/v1/projects/{projectId}/histories";

        // when
        var created = mockMvc.perform(post(historiesUrl, project.getId())
            .header("Authorization", "Bearer " + editorToken)
            .contentType(APPLICATION_JSON)
            .content("{\"description\":\"editor work\"}"));
        Long editorHistoryId = objectMapper.readTree(created.andReturn().getResponse()
            .getContentAsString()).path("result").path("historyId").asLong();
        var ownerList = mockMvc.perform(get(historiesUrl, project.getId())
            .header("Authorization", "Bearer " + ownerToken));
        var viewerList = mockMvc.perform(get(historiesUrl, project.getId())
            .header("Authorization", "Bearer " + viewerToken));
        var viewerEditorDetail = mockMvc.perform(get(historiesUrl + "/{historyId}",
            project.getId(), editorHistoryId)
            .header("Authorization", "Bearer " + viewerToken));
        var viewerLegacyDetail = mockMvc.perform(get(historiesUrl + "/{historyId}",
            project.getId(), legacy.getId())
            .header("Authorization", "Bearer " + viewerToken));
        var viewerCreate = mockMvc.perform(post(historiesUrl, project.getId())
            .header("Authorization", "Bearer " + viewerToken)
            .contentType(APPLICATION_JSON)
            .content("{\"description\":\"denied\"}"));
        var outsiderList = mockMvc.perform(get(historiesUrl, project.getId())
            .header("Authorization", "Bearer " + outsiderToken));

        // then
        created.andExpect(status().isCreated())
            .andExpect(jsonPath("$.result.versionName").value("v2"))
            .andExpect(jsonPath("$.result.actorMemberId").value(editor.getId()));
        ownerList.andExpect(status().isOk())
            .andExpect(jsonPath("$.result.historyList.length()").value(2));
        viewerList.andExpect(status().isOk())
            .andExpect(jsonPath("$.result.historyList.length()").value(2));
        var historyRows = objectMapper.readTree(viewerList.andReturn().getResponse()
            .getContentAsString()).path("result").path("historyList");
        boolean foundLegacy = false;
        boolean foundEditor = false;
        for (int index = 0; index < historyRows.size(); index++) {
            var row = historyRows.get(index);
            if (row.path("historyId").asLong() == legacy.getId()) {
                foundLegacy = true;
                assertTrue(row.path("actorMemberId").isNull());
            } else {
                foundEditor = true;
                assertEquals(editorHistoryId, row.path("historyId").asLong());
                assertEquals(editor.getId(), row.path("actorMemberId").asLong());
            }
        }
        assertTrue(foundLegacy);
        assertTrue(foundEditor);
        viewerEditorDetail.andExpect(status().isOk())
            .andExpect(jsonPath("$.result.actorMemberId").value(editor.getId()));
        viewerLegacyDetail.andExpect(status().isOk())
            .andExpect(jsonPath("$.result.actorMemberId").value(nullValue()));
        viewerCreate.andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PROJECT403_1"));
        outsiderList.andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PROJECT403_1"));
        assertEquals(editor.getId(), projectHistoryRepository.findById(editorHistoryId)
            .orElseThrow().getActorMemberId());
        assertEquals(2, projectHistoryRepository.countByProjectId(project.getId()));
    }

    @Test
    @DisplayName("EDITOR의 Generate 이력에는 프로젝트 owner가 아닌 EDITOR의 ID가 저장된다")
    void generate_Editor_SavesEditorAsHistoryActor() throws Exception {
        // given
        Member owner = saveMember("generate-owner@infragen.test");
        Member editor = saveMember("generate-editor@infragen.test");
        Project project = saveProject(owner, "editor-generate-project");
        projectCollaboratorRepository.saveAndFlush(ProjectCollaborator.builder()
            .project(project).member(editor).role(ProjectCollaboratorRole.EDITOR).build());
        saveDurableGraph(project, false);
        String editorToken = jwtUtil.createAccessToken(editor.getId(), editor.getRole());

        // when
        var response = mockMvc.perform(post(GENERATE_URL, project.getId())
            .header("Authorization", "Bearer " + editorToken)
            .contentType(APPLICATION_JSON)
            .content(REQUEST_JSON));

        // then
        response.andExpect(status().isOk())
            .andExpect(jsonPath("$.result.historyId").isNumber());
        Long historyId = objectMapper.readTree(response.andReturn().getResponse()
            .getContentAsString()).path("result").path("historyId").asLong();
        assertEquals(editor.getId(), projectHistoryRepository.findById(historyId)
            .orElseThrow().getActorMemberId());
    }

    @Test
    @DisplayName("동시 Generate 요청은 같은 project에서 중복 없는 history version을 저장한다")
    void generate_ConcurrentSameProject_SavesSequentialHistoryVersions() throws Exception {
        // given
        Member owner = saveMember("concurrent-owner@infragen.test");
        Project project = saveProject(owner, "concurrent-generate-project");
        saveDurableGraph(project, false);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> generateAfterStart(project, owner, start));
            var second = executor.submit(() -> generateAfterStart(project, owner, start));

            // when
            start.countDown();
            List<ResultActions> results = List.of(
                first.get(30, TimeUnit.SECONDS),
                second.get(30, TimeUnit.SECONDS)
            );

            // then
            results.forEach(result -> {
                try {
                    result
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value("GENERATION200_1"))
                        .andExpect(jsonPath("$.result.historyId").isNumber());
                } catch (Exception exception) {
                    throw new AssertionError("동시 Generate 요청이 성공해야 한다", exception);
                }
            });
        }

        List<ProjectHistory> histories = projectHistoryRepository
            .findAllByProjectIdOrderByCreatedAtDesc(project.getId());
        assertEquals(2, histories.size());
        assertEquals(List.of("v1", "v2"), histories.stream()
            .map(ProjectHistory::getVersionName)
            .sorted()
            .toList());
        assertEquals(4, generatedFileRepository.count());
    }

    @Test
    @DisplayName("MySQL + Redis 프로젝트 생성 — Compose·.env·history 저장")
    void generate_MysqlAndRedisProject_SavesAllGeneratedContracts() throws Exception {
        // given
        Member owner = saveMember("redis-owner@infragen.test");
        Project project = saveProject(owner, "mysql-redis-project");
        saveDurableGraph(project, true);

        // when
        ResultActions result = mockMvc.perform(post(GENERATE_URL, project.getId())
            .with(authenticatedAs(owner))
            .contentType(APPLICATION_JSON)
            .content(MYSQL_REDIS_REQUEST_JSON));

        // then
        result
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.code").value("GENERATION200_1"))
            .andExpect(jsonPath("$.result.historyId").isNumber())
            .andExpect(jsonPath("$.result.files.length()").value(2));

        ProjectHistory history = projectHistoryRepository
            .findAllByProjectIdOrderByCreatedAtDesc(project.getId())
            .getFirst();
        List<GeneratedFile> generatedFiles = generatedFileRepository
            .findAllByProjectHistoryId(history.getId());

        assertTrue(generatedFiles.stream().anyMatch(file ->
            "local/docker-compose.yml".equals(file.getFileName())
                && file.getContent().contains("durable_redis_data:/data")
                && file.getContent().contains("  durable_redis_data:\n")));
        assertTrue(generatedFiles.stream().anyMatch(file ->
            "local/.env".equals(file.getFileName())
                && file.getContent().contains("REDIS_HOST=localhost")
                && file.getContent().contains("REDIS_PORT=6379")
                && file.getContent().contains("REDIS_PASSWORD=durable-redis-password")));
        assertTrue(generatedFiles.stream().anyMatch(file ->
            "local/docker-compose.yml".equals(file.getFileName())
                && file.getContent().contains("durable-redis")));
    }

    @Test
    @DisplayName("대상 밖 필수 속성이 빠진 Redis — 요청 graph로 생성하고 저장 graph는 보존")
    void generate_ExcludedInvalidNode_UsesRequestedGraphAndPreservesStoredGraph() throws Exception {
        // given
        Member owner = saveMember("excluded-node-owner@infragen.test");
        Project project = saveProject(owner, "excluded-node-project");
        saveDurableGraph(project, false);

        List<ProjectNode> targetNodes = projectNodeRepository.findAllByProjectId(project.getId());
        ProjectNode springBoot = targetNodes.stream()
            .filter(node -> "durable-app-node".equals(node.getNodeId()))
            .findFirst()
            .orElseThrow();
        ProjectNode excludedRedis = projectNodeRepository.saveAndFlush(ProjectNode.builder()
            .project(project)
            .componentType(ComponentType.REDIS)
            .nodeId("excluded-redis-node")
            .nodeName("excluded-redis")
            .positionX(new java.math.BigDecimal("250"))
            .positionY(new java.math.BigDecimal("200"))
            .properties(Map.of(
                "imageVersion", "redis:7.4",
                "containerName", "excluded-redis",
                "port", 6379
            ))
            .build());
        projectEdgeRepository.saveAndFlush(ProjectEdge.builder()
            .project(project)
            .sourceNode(excludedRedis)
            .targetNode(springBoot)
            .build());

        // when
        ResultActions result = mockMvc.perform(post(GENERATE_URL, project.getId())
            .with(authenticatedAs(owner))
            .contentType(APPLICATION_JSON)
            .content(REQUEST_JSON));

        // then
        result
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.result.files.length()").value(2));

        String response = result.andReturn().getResponse().getContentAsString();
        assertFalse(objectMapper.readTree(response).path("result").path("files").toString()
            .contains("excluded-redis"));
        assertEquals(3, projectNodeRepository.findAllByProjectId(project.getId()).size());
        assertEquals(2, projectEdgeRepository.findAllByProjectId(project.getId()).size());
        assertEquals(1, projectHistoryRepository.countByProjectId(project.getId()));
    }

    @Test
    @DisplayName("타인 프로젝트 Generate — 쓰기 권한 오류로 거부")
    void generate_ProjectOwnedByAnotherMember_ReturnsForbidden() throws Exception {
        // given
        Member owner = saveMember("owner@infragen.test");
        Member otherMember = saveMember("other@infragen.test");
        Project project = saveProject(owner, "owner-project");

        // when
        ResultActions result = mockMvc.perform(post(GENERATE_URL, project.getId())
                .with(authenticatedAs(otherMember))
                .contentType(APPLICATION_JSON)
                .content(REQUEST_JSON));

        // then
        result
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.isSuccess").value(false))
            .andExpect(jsonPath("$.code").value("PROJECT403_1"));

        // then
        assertEquals(0, projectHistoryRepository
            .countByProjectId(project.getId()));
    }

    @Test
    @DisplayName("guest는 다른 guest를 초대해 편집 권한을 줄 수 있고 프로젝트 경계는 유지된다")
    void guestProjects_CreateSaveGenerateAndRemainIsolated() throws Exception {
        // given
        GuestSession guestA = issueGuestSession();
        GuestSession guestB = issueGuestSession();
        Long guestAProjectId = createGuestProject(guestA.accessToken(), "guest-a-project");
        Long guestBProjectId = createGuestProject(guestB.accessToken(), "guest-b-project");

        // when
        var saveResponse = mockMvc.perform(put("/api/v1/projects/{projectId}", guestAProjectId)
                .header("Authorization", "Bearer " + guestA.accessToken())
                .contentType(APPLICATION_JSON)
                .content(GUEST_GRAPH_JSON));
        var generateResponse = mockMvc.perform(post(GENERATE_URL, guestAProjectId)
                .header("Authorization", "Bearer " + guestA.accessToken())
                .contentType(APPLICATION_JSON)
                .content(REQUEST_JSON));
        String reissuedGuestAToken = reissueGuestToken(guestA.refreshToken());
        var guestAProjects = mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer " + reissuedGuestAToken));
        var guestBProjects = mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer " + guestB.accessToken()));
        var guestBPreInviteDetail = mockMvc.perform(get("/api/v1/projects/{projectId}", guestAProjectId)
                .header("Authorization", "Bearer " + guestB.accessToken()));
        String inviteeCode = objectMapper.readTree(mockMvc.perform(
                        post("/api/v1/members/me/invitation-code")
                                .header("Authorization", "Bearer " + guestB.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MEMBER200_5"))
                .andReturn()
                .getResponse()
                .getContentAsString()).path("result").path("inviteCode").asText();
        var inviteGuestB = mockMvc.perform(post("/api/v1/projects/{projectId}/collaborators/invitations", guestAProjectId)
                .header("Authorization", "Bearer " + reissuedGuestAToken)
                .contentType(APPLICATION_JSON)
                .content("{\"inviteeCode\":\"" + inviteeCode + "\",\"role\":\"EDITOR\"}"));
        String receivedInvitations = mockMvc.perform(get(
                        "/api/v1/project-collaborator-invitations/received")
                .header("Authorization", "Bearer " + guestB.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJECT200_10"))
                .andExpect(jsonPath("$.result.invitations.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long invitationId = objectMapper.readTree(receivedInvitations)
                .path("result").path("invitations").get(0).path("invitationId").asLong();
        var acceptGuestB = mockMvc.perform(post(
                        "/api/v1/project-collaborator-invitations/{invitationId}/accept", invitationId)
                .header("Authorization", "Bearer " + guestB.accessToken()));
        var guestACollaborators = mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/collaborators", guestAProjectId)
                .header("Authorization", "Bearer " + reissuedGuestAToken));
        var guestBEditorSave = mockMvc.perform(put("/api/v1/projects/{projectId}", guestAProjectId)
                .header("Authorization", "Bearer " + guestB.accessToken())
                .contentType(APPLICATION_JSON)
                .content(GUEST_GRAPH_JSON.replace("\"baseVersion\": 0", "\"baseVersion\": 1")));
        var guestBSharedProjects = mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer " + guestB.accessToken()));
        var guestAProjectDetail = mockMvc.perform(get("/api/v1/projects/{projectId}", guestAProjectId)
                .header("Authorization", "Bearer " + reissuedGuestAToken));
        var guestAHistory = mockMvc.perform(get("/api/v1/projects/{projectId}/histories", guestAProjectId)
                .header("Authorization", "Bearer " + reissuedGuestAToken));
        var foreignDetail = mockMvc.perform(get("/api/v1/projects/{projectId}", guestAProjectId)
                .header("Authorization", "Bearer " + guestB.accessToken()));
        var collaboratorHistory = mockMvc.perform(get("/api/v1/projects/{projectId}/histories", guestAProjectId)
                .header("Authorization", "Bearer " + guestB.accessToken()));

        // then
        saveResponse
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJECT200_3"));
        generateResponse
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GENERATION200_1"))
                .andExpect(jsonPath("$.result.historyId").isNumber());
        guestAProjects
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.projectList.length()").value(1))
                .andExpect(jsonPath("$.result.projectList[0].projectId").value(guestAProjectId));
        guestBProjects
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.projectList.length()").value(1))
                .andExpect(jsonPath("$.result.projectList[0].projectId").value(guestBProjectId));
        guestBPreInviteDetail
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT403_1"));
        inviteGuestB
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PROJECT201_3"));
        acceptGuestB
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJECT200_11"));
        guestACollaborators
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.collaborators.length()").value(1))
                .andExpect(jsonPath("$.result.collaborators[0].role").value("EDITOR"));
        guestBEditorSave
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJECT200_3"));
        guestBSharedProjects
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.projectList.length()").value(2));
        guestAProjectDetail
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJECT200_2"))
                .andExpect(jsonPath("$.result.nodes.length()").value(2));
        guestAHistory
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJECT_HISTORY200_1"))
                .andExpect(jsonPath("$.result.historyList.length()").value(1));
        foreignDetail
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJECT200_2"));
        collaboratorHistory
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJECT_HISTORY200_1"))
                .andExpect(jsonPath("$.result.historyList.length()").value(1));
        assertEquals(1, projectHistoryRepository.countByProjectId(guestAProjectId));
        assertEquals(0, projectHistoryRepository.countByProjectId(guestBProjectId));
    }

    @Test
    @DisplayName("저장 graph 없음 — 요청 graph로 생성하고 history 저장")
    void generate_WithoutStoredGraph_UsesRequestGraphAndSavesHistory() throws Exception {
        // given
        Member owner = saveMember("owner@infragen.test");
        Project project = saveProject(owner, "request-graph-project");

        // when
        ResultActions result = mockMvc.perform(post(GENERATE_URL, project.getId())
            .with(authenticatedAs(owner))
            .contentType(APPLICATION_JSON)
            .content(REQUEST_JSON));

        // then
        result
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.result.files.length()").value(2));
        assertEquals(1, projectHistoryRepository.countByProjectId(project.getId()));
        assertEquals(2, generatedFileRepository.count());
    }

    private Member saveMember(String email) {
        return memberRepository.saveAndFlush(Member.builder()
            .email(email)
            .password("encoded-password")
            .nickname("tester")
            .role(Role.ROLE_USER)
            .isActive(true)
            .build());
    }

    private GuestSession issueGuestSession() throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH200_2"))
                .andReturn();
        String response = result.getResponse().getContentAsString();
        String refreshCookie = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("refresh_token="))
                .findFirst()
                .orElseThrow()
                .split(";", 2)[0];
        String refreshToken = refreshCookie.substring("refresh_token=".length());
        return new GuestSession(
                objectMapper.readTree(response).path("result").path("accessToken").asText(),
                refreshToken
        );
    }

    private String reissueGuestToken(String refreshToken) throws Exception {
        String csrfToken = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("X-XSRF-TOKEN");
        String response = mockMvc.perform(post("/api/v1/auth/reissue")
                        .cookie(
                                new Cookie("refresh_token", refreshToken),
                                new Cookie("XSRF-TOKEN", csrfToken)
                        )
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH200_3"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("result").path("accessToken").asText();
    }

    private Long createGuestProject(String accessToken, String title) throws Exception {
        String response = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"description\":\"guest test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PROJECT201_1"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("result").path("projectId").asLong();
    }

    private Long getMemberId(String accessToken) throws Exception {
        String response = mockMvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("result").path("id").asLong();
    }

    private Project saveProject(Member member, String title) {
        return projectRepository.saveAndFlush(Project.builder()
            .title(title)
            .description("integration test project")
            .status(ProjectStatus.DRAFT)
            .member(member)
            .build());
    }

    private ResultActions generateAfterStart(
        Project project,
        Member owner,
        CountDownLatch start
    ) throws Exception {
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("동시 Generate 요청 시작 대기 초과");
        }
        return mockMvc.perform(post(GENERATE_URL, project.getId())
            .with(authenticatedAs(owner))
            .contentType(APPLICATION_JSON)
            .content(REQUEST_JSON));
    }

    private void saveDurableGraph(Project project, boolean includeRedis) {
        ProjectNode mysql = ProjectNode.builder()
            .project(project)
            .componentType(ComponentType.MYSQL)
            .nodeId("durable-mysql-node")
            .nodeName("durable-mysql")
            .positionX(new java.math.BigDecimal("100"))
            .positionY(new java.math.BigDecimal("200"))
            .properties(Map.of(
                "imageVersion", "mysql:8.0",
                "containerName", "durable-mysql",
                "volumeName", "durable_mysql_data",
                "port", 3306,
                "env", Map.of(
                    "databaseName", "durable_db",
                    "username", "durable_user",
                    "userPassword", "durablepass12",
                    "rootPassword", "durableroot12"
                )
            ))
            .build();
        ProjectNode springBoot = ProjectNode.builder()
            .project(project)
            .componentType(ComponentType.SPRING_BOOT)
            .nodeId("durable-app-node")
            .nodeName("durable-app")
            .positionX(new java.math.BigDecimal("400"))
            .positionY(new java.math.BigDecimal("200"))
            .properties(Map.of(
                "name", "durable-app",
                "port", 8080,
                "javaVersion", "17",
                "containerName", "durable-app"
            ))
            .build();

        List<ProjectNode> nodes = includeRedis
            ? List.of(mysql, saveRedisNode(project), springBoot)
            : List.of(mysql, springBoot);
        projectNodeRepository.saveAllAndFlush(nodes);

        if (includeRedis) {
            ProjectNode redis = nodes.get(1);
            projectEdgeRepository.saveAndFlush(ProjectEdge.builder()
                .project(project)
                .sourceNode(mysql)
                .targetNode(springBoot)
                .build());
            projectEdgeRepository.saveAndFlush(ProjectEdge.builder()
                .project(project)
                .sourceNode(redis)
                .targetNode(springBoot)
                .build());
            return;
        }

        projectEdgeRepository.saveAndFlush(ProjectEdge.builder()
            .project(project)
            .sourceNode(mysql)
            .targetNode(springBoot)
            .build());
    }

    private ProjectNode saveRedisNode(Project project) {
        return ProjectNode.builder()
            .project(project)
            .componentType(ComponentType.REDIS)
            .nodeId("durable-redis-node")
            .nodeName("durable-redis")
            .positionX(new java.math.BigDecimal("250"))
            .positionY(new java.math.BigDecimal("200"))
            .properties(Map.of(
                "imageVersion", "redis:7.4",
                "containerName", "durable-redis",
                "volumeName", "durable_redis_data",
                "port", 6379,
                "password", "durable-redis-password"
            ))
            .build();
    }

    private static RequestPostProcessor authenticatedAs(Member member) {
        MemberResDTO.MemberResultDTO memberDTO = MemberResDTO.MemberResultDTO.builder()
            .id(member.getId())
            .email(member.getEmail())
            .nickname(member.getNickname())
            .role(member.getRole())
            .isActive(member.getIsActive())
            .build();
        CustomUserDetails userDetails = new CustomUserDetails(memberDTO);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            userDetails.getAuthorities()
        );
        return authentication(authentication);
    }

    private record GuestSession(String accessToken, String refreshToken) {
    }
}
