package com.infragen.infragen.domain.project.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.dto.request.ProjectHistoryReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectHistoryResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectHistory;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.repository.GeneratedFileRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.repository.ProjectHistoryRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;

@Testcontainers
@DataJpaTest(properties = {
    "spring.docker.compose.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ProjectHistoryConcurrencyIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProjectHistoryConcurrencyIntegrationTest {
    private static final String MYSQL_IMAGE =
        "mysql:8.4.6@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql")
    )
        .withDatabaseName("infragen_issue50_test")
        .withUsername("infragen_test")
        .withPassword("infragen_test_password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private ProjectHistoryCommandService historyCommandService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectHistoryRepository projectHistoryRepository;

    @Autowired
    private GeneratedFileRepository generatedFileRepository;

    @Autowired
    private ProjectCollaboratorRepository projectCollaboratorRepository;

    @Autowired
    private ProjectNodeRepository projectNodeRepository;

    @Autowired
    private ProjectEdgeRepository projectEdgeRepository;

    @Configuration
    @EnableJpaAuditing
    @EntityScan(basePackages = {
        "com.infragen.infragen.domain.member.entity",
        "com.infragen.infragen.domain.project.entity"
    })
    @EnableJpaRepositories(basePackages = {
        "com.infragen.infragen.domain.member.repository",
        "com.infragen.infragen.domain.project.repository"
    })
    @Import({
        ProjectHistoryCommandService.class,
        ProjectQueryService.class,
        ProjectAccessService.class
    })
    static class Config {
    }

    @AfterEach
    void tearDown() {
        generatedFileRepository.deleteAllInBatch();
        projectHistoryRepository.deleteAllInBatch();
        projectEdgeRepository.deleteAllInBatch();
        projectNodeRepository.deleteAllInBatch();
        projectCollaboratorRepository.deleteAllInBatch();
        projectRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("같은 project의 동시 수동 history 생성은 고유한 순차 version을 발급한다")
    void createHistory_ConcurrentSameProject_ReturnsSequentialVersions() throws Exception {
        // given
        Fixture fixture = fixture("manual-history");
        ProjectHistoryReqDTO.CreateHistoryReqDTO request =
            new ProjectHistoryReqDTO.CreateHistoryReqDTO("concurrent history");

        // when
        List<ProjectHistoryResDTO.HistoryPreviewResDTO> results = runConcurrently(List.of(
            () -> historyCommandService.createHistory(fixture.projectId(), request, fixture.memberId()),
            () -> historyCommandService.createHistory(fixture.projectId(), request, fixture.memberId())
        ));

        // then
        assertEquals(List.of("v1", "v2"), results.stream()
            .map(ProjectHistoryResDTO.HistoryPreviewResDTO::versionName)
            .sorted()
            .toList());
        assertEquals(2, projectHistoryRepository.countByProjectId(fixture.projectId()));
    }

    @Test
    @DisplayName("같은 project의 동시 Generate history 저장은 version 중복 없이 파일을 저장한다")
    void saveGeneratedHistory_ConcurrentSameProject_SavesUniqueVersions() throws Exception {
        // given
        Fixture fixture = fixture("generated-history");
        List<IaCFileDTO.FileContentResDTO> generatedFiles = List.of(
            IaCFileDTO.FileContentResDTO.builder()
                .fileName("local/docker-compose.yml")
                .content("services: {}")
                .build()
        );

        // when
        List<Long> historyIds = runConcurrently(List.of(
            () -> historyCommandService.saveGeneratedHistory(
                fixture.projectId(), fixture.memberId(), generatedFiles),
            () -> historyCommandService.saveGeneratedHistory(
                fixture.projectId(), fixture.memberId(), generatedFiles)
        ));

        // then
        assertEquals(2, historyIds.stream().distinct().count());
        List<ProjectHistory> histories = projectHistoryRepository
            .findAllByProjectIdOrderByCreatedAtDesc(fixture.projectId());
        assertEquals(List.of("v1", "v2"), histories.stream()
            .map(ProjectHistory::getVersionName)
            .sorted()
            .toList());
        assertEquals(2, generatedFileRepository.count());
    }

    @Test
    @DisplayName("서로 다른 project의 동시 history 생성은 각 project의 version을 독립적으로 발급한다")
    void createHistory_ConcurrentDifferentProjects_IsolatesVersions() throws Exception {
        // given
        Fixture first = fixture("first-project");
        Fixture second = fixture("second-project");
        ProjectHistoryReqDTO.CreateHistoryReqDTO request =
            new ProjectHistoryReqDTO.CreateHistoryReqDTO("isolated history");

        // when
        List<ProjectHistoryResDTO.HistoryPreviewResDTO> results = runConcurrently(List.of(
            () -> historyCommandService.createHistory(first.projectId(), request, first.memberId()),
            () -> historyCommandService.createHistory(second.projectId(), request, second.memberId())
        ));

        // then
        assertTrue(results.stream().allMatch(result -> "v1".equals(result.versionName())));
        assertEquals(1, projectHistoryRepository.countByProjectId(first.projectId()));
        assertEquals(1, projectHistoryRepository.countByProjectId(second.projectId()));
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(tasks.size())) {
            List<java.util.concurrent.Future<T>> futures = tasks.stream()
                .map(task -> executor.submit(() -> {
                    ready.countDown();
                    if (!ready.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("동시성 테스트 작업 준비 대기 초과");
                    }
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("동시성 테스트 시작 대기 초과");
                    }
                    return task.call();
                }))
                .toList();

            assertTrue(ready.await(10, TimeUnit.SECONDS), "모든 동시성 테스트 작업이 준비되어야 한다");
            start.countDown();
            return futures.stream()
                .map(future -> get(future, 20))
                .toList();
        }
    }

    private <T> T get(java.util.concurrent.Future<T> future, long timeoutSeconds) {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("동시성 테스트 작업이 제한 시간 안에 완료되어야 한다", exception);
        }
    }

    private Fixture fixture(String title) {
        Member member = memberRepository.saveAndFlush(Member.builder()
            .email(UUID.randomUUID() + "@issue50.test")
            .password("test-only")
            .nickname("issue50-tester")
            .role(Role.ROLE_USER)
            .isActive(true)
            .build());
        Project project = projectRepository.saveAndFlush(Project.builder()
            .title(title)
            .description("Issue #50 concurrency test")
            .status(ProjectStatus.DRAFT)
            .member(member)
            .build());
        return new Fixture(project.getId(), member.getId());
    }

    private record Fixture(Long projectId, Long memberId) {
    }
}
