package com.infragen.infragen.domain.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.entity.GeneratedFile;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectHistory;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.repository.GeneratedFileRepository;
import com.infragen.infragen.domain.project.repository.ProjectHistoryRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
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
    private static final String REQUEST_JSON = """
        {
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
    private MemberRepository memberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectHistoryRepository projectHistoryRepository;

    @Autowired
    private GeneratedFileRepository generatedFileRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        generatedFileRepository.deleteAllInBatch();
        projectHistoryRepository.deleteAllInBatch();
        projectRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("소유 프로젝트 생성 — 파일과 historyId를 반환하고 DB에 저장")
    void generate_OwnedProject_SavesFilesAndReturnsHistoryId() throws Exception {
        // given
        Member owner = saveMember("owner@infragen.test");
        Project project = saveProject(owner, "owned-project");

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
            "docker-compose.yml".equals(file.getFileName())
                && file.getContent().contains("services:")));
        assertTrue(generatedFiles.stream().anyMatch(file ->
            ".env".equals(file.getFileName())
                && file.getContent().contains("localhost")));
    }

    @Test
    @DisplayName("MySQL + Redis 프로젝트 생성 — Compose·.env·history 저장")
    void generate_MysqlAndRedisProject_SavesAllGeneratedContracts() throws Exception {
        // given
        Member owner = saveMember("redis-owner@infragen.test");
        Project project = saveProject(owner, "mysql-redis-project");

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
            "docker-compose.yml".equals(file.getFileName())
                && file.getContent().contains("redis_data:/data")
                && file.getContent().contains("  redis_data:\n")));
        assertTrue(generatedFiles.stream().anyMatch(file ->
            ".env".equals(file.getFileName())
                && file.getContent().contains("REDIS_HOST=localhost")
                && file.getContent().contains("REDIS_PORT=6379")
                && file.getContent().contains("REDIS_PASSWORD=test-redis-password")));
    }

    @Test
    @DisplayName("타인 프로젝트 생성 — 프로젝트를 찾을 수 없어 404 반환")
    void generate_ProjectOwnedByAnotherMember_ReturnsNotFound() throws Exception {
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
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.isSuccess").value(false))
            .andExpect(jsonPath("$.code").value("PROJECT404_1"));

        // then
        assertEquals(0, projectHistoryRepository
            .countByProjectId(project.getId()));
    }

    @Test
    @DisplayName("필수 파싱값 누락 — 400 반환 및 생성 결과 미저장")
    void generate_MissingMySqlUsername_ReturnsBadRequestWithoutSavingHistory() throws Exception {
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
            .andExpect(jsonPath("$.code").value("PARSING400_18"));
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

    private Project saveProject(Member member, String title) {
        return projectRepository.saveAndFlush(Project.builder()
            .title(title)
            .description("integration test project")
            .status(ProjectStatus.DRAFT)
            .member(member)
            .build());
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
}
