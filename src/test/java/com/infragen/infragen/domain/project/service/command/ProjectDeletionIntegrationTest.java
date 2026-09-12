package com.infragen.infragen.domain.project.service.command;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationCheckpointFailure;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationSnapshot;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationState;
import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationCheckpointFailureRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationSnapshotRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationStateRepository;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.entity.GeneratedFile;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.entity.ProjectEdge;
import com.infragen.infragen.domain.project.entity.ProjectHistory;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.repository.ProjectHistoryRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.global.enums.ComponentType;
import com.infragen.infragen.global.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 격리된 MySQL의 실제 FK와 HTTP 인증을 사용해 프로젝트 삭제 범위와 transaction 롤백을 검증한다.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProjectDeletionIntegrationTest {
    private static final List<String> PROJECT_CHILD_TABLES = List.of(
            "project_collaboration_checkpoint_failure", "project_collaboration_snapshot",
            "project_collaboration_operation", "project_collaboration_state", "project_collaborator",
            "project_history", "project_edge", "project_node");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse(
            "mysql:8.4.6@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9")
            .asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("infragen_project_deletion_test")
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
    private ProjectCommandService projectCommandService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectNodeRepository nodeRepository;
    @Autowired
    private ProjectEdgeRepository edgeRepository;
    @Autowired
    private ProjectHistoryRepository historyRepository;
    @Autowired
    private ProjectCollaboratorRepository collaboratorRepository;
    @Autowired
    private ProjectCollaborationStateRepository stateRepository;
    @Autowired
    private ProjectCollaborationOperationRepository operationRepository;
    @Autowired
    private ProjectCollaborationSnapshotRepository snapshotRepository;
    @Autowired
    private ProjectCollaborationCheckpointFailureRepository failureRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtUtil jwtUtil;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM generated_file");
        for (String table : PROJECT_CHILD_TABLES) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM member");
    }

    @Test
    @DisplayName("협업 데이터가 있는 프로젝트를 삭제하면 대상만 정리하고 다른 프로젝트와 회원은 보존한다")
    void deleteProject_WithAllDependencies_DeletesOnlyTargetProject() {
        // given
        Member owner = saveMember();
        Member editor = saveMember();
        ProjectFixture target = saveProjectWithDependencies(owner, editor);
        ProjectFixture other = saveProjectWithDependencies(owner, editor);
        Map<String, List<Map<String, Object>>> targetBefore = storedRows(target);
        Map<String, List<Map<String, Object>>> otherBefore = storedRows(other);

        // when
        JsonNode response = deleteProject(owner, target.project().getId());
        Map<String, List<Map<String, Object>>> targetAfter = storedRows(target);

        // then
        assertAll(
                () -> assertTrue(targetBefore.values().stream().allMatch(rows -> !rows.isEmpty())),
                () -> assertTrue(response.get("isSuccess").asBoolean()),
                () -> assertEquals("PROJECT200_4", response.get("code").asString()),
                () -> assertTrue(targetAfter.values().stream().allMatch(List::isEmpty)),
                () -> assertEquals(otherBefore, storedRows(other)),
                () -> assertTrue(memberRepository.existsById(owner.getId())),
                () -> assertTrue(memberRepository.existsById(editor.getId()))
        );
    }

    @Test
    @DisplayName("협업 데이터가 없는 빈 프로젝트도 기존 응답으로 삭제한다")
    void deleteProject_WithoutDependencies_Succeeds() {
        // given
        Member owner = saveMember();
        Project project = saveProject(owner);

        // when
        JsonNode response = deleteProject(owner, project.getId());

        // then
        assertAll(
                () -> assertEquals("PROJECT200_4", response.get("code").asString()),
                () -> assertFalse(projectRepository.existsById(project.getId())),
                () -> assertTrue(memberRepository.existsById(owner.getId()))
        );
    }

    @Test
    @DisplayName("EDITOR도 owner의 프로젝트를 삭제할 수 없고 모든 데이터가 유지된다")
    void deleteProject_ByEditor_PreservesAllRows() {
        // given
        Member owner = saveMember();
        Member editor = saveMember();
        ProjectFixture target = saveProjectWithDependencies(owner, editor);
        Map<String, List<Map<String, Object>>> before = storedRows(target);

        // when
        HttpClientErrorException.NotFound failure = assertThrows(HttpClientErrorException.NotFound.class,
                () -> deleteProject(editor, target.project().getId()));

        // then
        assertAll(
                () -> assertEquals(404, failure.getStatusCode().value()),
                () -> assertEquals(before, storedRows(target))
        );
    }

    @Test
    @DisplayName("마지막 프로젝트 삭제가 FK 오류로 실패하면 먼저 삭제한 모든 종속 데이터도 롤백된다")
    void deleteProject_ParentDeleteFailure_RollsBackAllDependencies() {
        // given
        Member owner = saveMember();
        Member editor = saveMember();
        ProjectFixture target = saveProjectWithDependencies(owner, editor);
        Map<String, List<Map<String, Object>>> before = storedRows(target);
        // 테스트 전용 FK로 마지막 parent DELETE만 거부한다.
        jdbcTemplate.execute("CREATE TABLE project_delete_guard (project_id BIGINT NOT NULL, "
                + "CONSTRAINT fk_project_delete_guard FOREIGN KEY (project_id) REFERENCES project(id)) ENGINE=InnoDB");
        try {
            jdbcTemplate.update("INSERT INTO project_delete_guard(project_id) VALUES (?)", target.project().getId());

            // when
            DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                    () -> projectCommandService.deleteProject(target.project().getId(), owner.getId()));
            Map<String, List<Map<String, Object>>> after = storedRows(target);

            // then
            assertAll(
                    () -> assertTrue(failure.getMostSpecificCause().getMessage().contains("fk_project_delete_guard")),
                    () -> assertEquals(before, after)
            );
        } finally {
            jdbcTemplate.execute("DROP TABLE project_delete_guard");
        }
    }

    private ProjectFixture saveProjectWithDependencies(Member owner, Member editor) {
        Project project = saveProject(owner);
        ProjectNode database = nodeRepository.saveAndFlush(ProjectNode.builder().project(project)
                .nodeId("database").nodeName("mysql").componentType(ComponentType.MYSQL)
                .positionX(BigDecimal.ZERO).positionY(BigDecimal.ZERO).properties(Map.of("port", 3306)).build());
        ProjectNode application = nodeRepository.saveAndFlush(ProjectNode.builder().project(project)
                .nodeId("application").nodeName("app").componentType(ComponentType.SPRING_BOOT)
                .positionX(BigDecimal.ONE).positionY(BigDecimal.ONE).properties(Map.of("port", 8080)).build());
        edgeRepository.saveAndFlush(ProjectEdge.builder().project(project)
                .sourceNode(database).targetNode(application).build());
        ProjectHistory history = ProjectHistory.builder().project(project).versionName("v1").build();
        history.addGeneratedFile(GeneratedFile.builder().fileName("compose.yaml").filePath("local/compose.yaml")
                .content("services: {}").fileSize(12).build());
        history = historyRepository.saveAndFlush(history);
        collaboratorRepository.saveAndFlush(ProjectCollaborator.builder().project(project)
                .member(editor).role(ProjectCollaboratorRole.EDITOR).build());
        ProjectCollaborationState state = new ProjectCollaborationState(project);
        state.advanceServerVersion();
        stateRepository.saveAndFlush(state);
        operationRepository.saveAndFlush(ProjectCollaborationOperation.builder().project(project).actorMember(editor)
                .operationId("operation-1").clientId("client-1").baseVersion(0L).serverVersion(1L)
                .operationType(CollaborationOperationType.UPDATE_NODE_NAME).nodeId("database")
                .payload(Map.of("value", "mysql")).build());
        snapshotRepository.saveAndFlush(ProjectCollaborationSnapshot.builder().project(project).updatedBy(editor)
                .serverVersion(1L).graphPayload(Map.of("projectId", project.getId())).build());
        failureRepository.saveAndFlush(ProjectCollaborationCheckpointFailure.builder().project(project).member(editor)
                .serverVersion(2L).graphPayload(Map.of("projectId", project.getId()))
                .attemptCount(1).lastError("test checkpoint failure").build());
        return new ProjectFixture(project, history.getId());
    }

    private Map<String, List<Map<String, Object>>> storedRows(ProjectFixture fixture) {
        Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
        Long projectId = fixture.project().getId();
        rows.put("project", jdbcTemplate.queryForList("SELECT * FROM project WHERE id = ?", projectId));
        // history가 삭제되어도 남은 generated file을 탐지할 수 있도록 원래 history ID로 조회한다.
        rows.put("generated_file", jdbcTemplate.queryForList(
                "SELECT * FROM generated_file WHERE history_id = ? ORDER BY id", fixture.historyId()));
        for (String table : PROJECT_CHILD_TABLES) {
            rows.put(table, jdbcTemplate.queryForList("SELECT * FROM " + table + " WHERE project_id = ? ORDER BY id", projectId));
        }
        return rows;
    }

    private JsonNode deleteProject(Member member, Long projectId) {
        return RestClient.builder().baseUrl("http://localhost:" + serverPort)
                .defaultHeaders(headers -> headers.setBearerAuth(jwtUtil.createAccessToken(member.getId(), member.getRole())))
                .build().delete().uri("/api/v1/projects/{projectId}", projectId)
                .retrieve().body(JsonNode.class);
    }

    private Project saveProject(Member owner) {
        return projectRepository.saveAndFlush(Project.builder().member(owner).title("deletion-test")
                .description("project with dependencies").status(ProjectStatus.DRAFT).build());
    }

    private Member saveMember() {
        return memberRepository.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@infragen.test")
                .password("encoded-test-password").nickname("deletion-test-member")
                .role(Role.ROLE_USER).isActive(true).build());
    }

    private record ProjectFixture(Project project, Long historyId) {
    }
}
