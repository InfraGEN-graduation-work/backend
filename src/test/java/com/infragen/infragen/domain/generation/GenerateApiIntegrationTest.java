package com.infragen.infragen.domain.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.infragen.infragen.domain.project.entity.ProjectEdge;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.entity.ProjectHistory;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.repository.GeneratedFileRepository;
import com.infragen.infragen.domain.project.repository.ProjectHistoryRepository;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.global.enums.ComponentType;
import com.infragen.infragen.global.auth.CustomUserDetails;

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
              "nodeId": "node-1",
              "componentType": "MYSQL",
              "positionX": 100,
              "positionY": 200,
              "properties": {
                "imageVersion": "mysql:8.0",
                "containerName": "mysql",
                "volumeName": "mysql_data",
                "port": 3306,
                "env": {
                  "databaseName": "appdb",
                  "username": "user",
                  "userPassword": "userpass12",
                  "rootPassword": "rootpass12"
                }
              }
            },
            {
              "nodeId": "node-2",
              "componentType": "SPRING_BOOT",
              "positionX": 400,
              "positionY": 200,
              "properties": {
                "name": "app",
                "port": 8080,
                "javaVersion": "17",
                "containerName": "spring-app"
              }
            }
          ],
          "edges": [
            { "sourceNodeId": "node-1", "targetNodeId": "node-2" }
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
              "nodeId": "node-1",
              "componentType": "MYSQL",
              "positionX": 100,
              "positionY": 200,
              "properties": {
                "imageVersion": "mysql:8.0",
                "containerName": "mysql",
                "volumeName": "mysql_data",
                "port": 3306,
                "env": {
                  "databaseName": "appdb",
                  "username": "user",
                  "userPassword": "userpass12",
                  "rootPassword": "rootpass12"
                }
              }
            },
            {
              "nodeId": "node-2",
              "componentType": "REDIS",
              "positionX": 250,
              "positionY": 200,
              "properties": {
                "imageVersion": "redis:7.4",
                "containerName": "redis",
                "volumeName": "redis_data",
                "port": 6379,
                "password": "test-redis-password"
              }
            },
            {
              "nodeId": "node-3",
              "componentType": "SPRING_BOOT",
              "positionX": 400,
              "positionY": 200,
              "properties": {
                "name": "app",
                "port": 8080,
                "javaVersion": "17",
                "containerName": "spring-app"
              }
            }
          ],
          "edges": [
            { "sourceNodeId": "node-1", "targetNodeId": "node-3" },
            { "sourceNodeId": "node-2", "targetNodeId": "node-3" }
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
    private MemberRepository memberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectCollaboratorRepository projectCollaboratorRepository;

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
        Long guestBMemberId = getMemberId(guestB.accessToken());

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
        var inviteGuestB = mockMvc.perform(post("/api/v1/projects/{projectId}/collaborators", guestAProjectId)
                .header("Authorization", "Bearer " + reissuedGuestAToken)
                .contentType(APPLICATION_JSON)
                .content("{\"memberId\":" + guestBMemberId + ",\"role\":\"EDITOR\"}"));
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
        var foreignHistory = mockMvc.perform(get("/api/v1/projects/{projectId}/histories", guestAProjectId)
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
                .andExpect(jsonPath("$.code").value("PROJECT201_2"))
                .andExpect(jsonPath("$.result.memberId").value(guestBMemberId))
                .andExpect(jsonPath("$.result.role").value("EDITOR"));
        guestACollaborators
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.collaborators.length()").value(1))
                .andExpect(jsonPath("$.result.collaborators[0].memberId").value(guestBMemberId));
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
        foreignHistory
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT404_1"));
        assertEquals(1, projectHistoryRepository.countByProjectId(guestAProjectId));
        assertEquals(0, projectHistoryRepository.countByProjectId(guestBProjectId));
    }

    @Test
    @DisplayName("저장 graph 없음 — request body와 무관하게 400 반환")
    void generate_WithoutStoredGraph_ReturnsBadRequestWithoutSavingHistory() throws Exception {
        // given
        Member owner = saveMember("owner@infragen.test");
        Project project = saveProject(owner, "invalid-project");
        String invalidRequest = REQUEST_JSON.replace(
            "\"username\": \"user\"",
            "\"username\": \" \""
        );

        // when
        ResultActions result = mockMvc.perform(post(GENERATE_URL, project.getId())
            .with(authenticatedAs(owner))
            .contentType(APPLICATION_JSON)
            .content(invalidRequest));

        // then
        result
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.isSuccess").value(false))
            .andExpect(jsonPath("$.code").value("PARSING400_1"));
        assertEquals(0, projectHistoryRepository
            .countByProjectId(project.getId()));
        assertEquals(0, generatedFileRepository.count());
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
        String refreshCookie = result.getResponse().getHeader("Set-Cookie").split(";", 2)[0];
        String refreshToken = refreshCookie.substring("refresh_token=".length());
        return new GuestSession(
                objectMapper.readTree(response).path("result").path("accessToken").asText(),
                refreshToken
        );
    }

    private String reissueGuestToken(String refreshToken) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/reissue")
                        .cookie(new Cookie("refresh_token", refreshToken)))
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
