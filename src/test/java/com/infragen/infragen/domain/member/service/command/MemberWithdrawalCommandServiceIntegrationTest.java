package com.infragen.infragen.domain.member.service.command;

import com.infragen.infragen.domain.auth.service.TokenService;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.entity.ProjectCollaboratorInvitation;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorInvitationRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * 실제 MySQL에서 일반 회원 탈퇴 조립 흐름의 승계·삭제·초대 취소·참여 정리 결과, 실패 시 전체 롤백,
 * 서로에게 승계하는 동시 탈퇴의 교착 여부를 확인한다. Redis 토큰 서비스는 mock이다.
 */
@Testcontainers
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
class MemberWithdrawalCommandServiceIntegrationTest {
    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse(
            "mysql:8.4.6@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9")
            .asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("infragen_withdrawal_test")
            .withUsername("infragen_test").withPassword("infragen_test_password");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> 6379);
        registry.add("spring.data.redis.password", () -> "");
        registry.add("jwt.secret", () -> "test-jwt-secret-test-jwt-secret-test-jwt-secret-1234567890");
        registry.add("kakao.client-id", () -> "test-kakao-client-id");
        registry.add("kakao.client-secret", () -> "test-kakao-client-secret");
        registry.add("kakao.redirect-uri", () -> "http://localhost/test-callback");
        registry.add("kakao.authorization-uri", () -> "http://localhost/kakao/authorize");
        registry.add("kakao.token-uri", () -> "http://localhost/kakao/token");
        registry.add("kakao.user-info-uri", () -> "http://localhost/kakao/user-info");
    }

    @Autowired
    private MemberWithdrawalCommandService withdrawalService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectCollaboratorRepository collaboratorRepository;
    @Autowired
    private ProjectCollaboratorInvitationRepository invitationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM project_collaborator_invitation");
        jdbcTemplate.update("DELETE FROM project_collaborator");
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM member");
    }

    @Test
    @DisplayName("여러 프로젝트가 섞인 탈퇴는 승계·삭제·참여 정리·초대 취소를 한 번에 반영하고 이력은 보존한다")
    void withdraw_MixedProjects_AppliesAllStepsAndKeepsHistory() {
        // given
        Member departing = member("departing", Role.ROLE_USER);
        Member regular = member("regular", Role.ROLE_USER);
        Member guest = member("guest", Role.ROLE_GUEST);
        Member otherOwner = member("other-owner", Role.ROLE_USER);
        Project withRegularAndGuest = project(departing);
        Project withGuestOnly = project(departing);
        Project withNobody = project(departing);
        Project participating = project(otherOwner);
        Project invitedOnly = project(otherOwner);
        collaborator(withRegularAndGuest, guest, ProjectCollaboratorRole.EDITOR);
        collaborator(withRegularAndGuest, regular, ProjectCollaboratorRole.VIEWER);
        collaborator(withGuestOnly, guest, ProjectCollaboratorRole.VIEWER);
        collaborator(participating, departing, ProjectCollaboratorRole.VIEWER);
        ProjectCollaboratorInvitation sentByDeparting = invitation(withRegularAndGuest, departing, otherOwner);
        ProjectCollaboratorInvitation receivedByDeparting = invitation(invitedOnly, otherOwner, departing);
        ProjectCollaboratorInvitation alreadyDeclined = invitation(invitedOnly, otherOwner, departing);
        jdbcTemplate.update("UPDATE project_collaborator_invitation SET status = 'DECLINED' WHERE id = ?",
                alreadyDeclined.getId());

        // when
        withdrawalService.withdraw(departing.getId(), Set.of(withNobody.getId()));

        // then
        assertFalse(isActive(departing.getId()));
        assertNotNull(deletedAtOf(departing.getId()));
        assertTrue(memberRepository.findById(departing.getId()).isEmpty());
        assertEquals(regular.getId(), ownerOf(withRegularAndGuest.getId()));
        assertEquals(guest.getId(), ownerOf(withGuestOnly.getId()));
        assertEquals(0, count("SELECT COUNT(*) FROM project WHERE id = ?", withNobody.getId()));
        assertEquals(otherOwner.getId(), ownerOf(participating.getId()));
        assertEquals(List.of(guest.getId()), membersOf(withRegularAndGuest.getId()));
        assertEquals(List.of(), membersOf(withGuestOnly.getId()));
        assertEquals(List.of(), membersOf(participating.getId()));
        assertEquals("CANCELLED", statusOf(sentByDeparting.getId()));
        assertEquals("CANCELLED", statusOf(receivedByDeparting.getId()));
        assertEquals("DECLINED", statusOf(alreadyDeclined.getId()));
        assertEquals(departing.getId(), jdbcTemplate.queryForObject(
                "SELECT responded_by_member_id FROM project_collaborator_invitation WHERE id = ?",
                Long.class, sentByDeparting.getId()));
        verify(tokenService).deleteRefreshToken(departing.getId());
    }

    @Test
    @DisplayName("guest 이용 종료는 활성 guest에게 무작위 승계하고 후보 없는 프로젝트는 삭제하며 참여·대기 초대를 정리한다")
    void withdraw_GuestTermination_SucceedsToGuestAndCleansUp() {
        // given
        Member departing = member("departing-guest", Role.ROLE_GUEST);
        Member guestEditor = member("guest-editor", Role.ROLE_GUEST);
        Member guestViewer = member("guest-viewer", Role.ROLE_GUEST);
        Member otherGuestOwner = member("other-guest-owner", Role.ROLE_GUEST);
        Project withGuests = project(departing);
        Project withNobody = project(departing);
        Project participating = project(otherGuestOwner);
        Project invitedOnly = project(otherGuestOwner);
        collaborator(withGuests, guestEditor, ProjectCollaboratorRole.EDITOR);
        collaborator(withGuests, guestViewer, ProjectCollaboratorRole.VIEWER);
        collaborator(participating, departing, ProjectCollaboratorRole.VIEWER);
        ProjectCollaboratorInvitation receivedByDeparting = invitation(invitedOnly, otherGuestOwner, departing);

        // when
        withdrawalService.withdraw(departing.getId(), Set.of(withNobody.getId()));

        // then
        Long newOwnerId = ownerOf(withGuests.getId());
        Long remainingGuestId = newOwnerId.equals(guestEditor.getId()) ? guestViewer.getId() : guestEditor.getId();
        assertAll(
                () -> assertTrue(List.of(guestEditor.getId(), guestViewer.getId()).contains(newOwnerId)),
                () -> assertEquals(List.of(remainingGuestId), membersOf(withGuests.getId())),
                () -> assertEquals(0, count("SELECT COUNT(*) FROM project WHERE id = ?", withNobody.getId())),
                () -> assertEquals(List.of(), membersOf(participating.getId())),
                () -> assertEquals("CANCELLED", statusOf(receivedByDeparting.getId())),
                () -> assertFalse(isActive(departing.getId())),
                () -> assertNotNull(deletedAtOf(departing.getId())),
                () -> assertEquals("탈퇴회원", jdbcTemplate.queryForObject(
                        "SELECT nickname FROM member WHERE id = ?", String.class, departing.getId())));
        verify(tokenService).deleteRefreshToken(departing.getId());
    }

    @Test
    @DisplayName("마지막 토큰 삭제가 실패하면 승계·삭제·참여 정리·초대 취소·비활성화가 모두 롤백된다")
    void withdraw_TokenDeletionFails_RollsBackEverything() {
        // given
        Member departing = member("departing", Role.ROLE_USER);
        Member regular = member("regular", Role.ROLE_USER);
        Member otherOwner = member("other-owner", Role.ROLE_USER);
        Project transferred = project(departing);
        Project deleted = project(departing);
        Project participating = project(otherOwner);
        collaborator(transferred, regular, ProjectCollaboratorRole.EDITOR);
        collaborator(participating, departing, ProjectCollaboratorRole.EDITOR);
        ProjectCollaboratorInvitation pending = invitation(participating, otherOwner, departing);
        doThrow(new IllegalStateException("redis failed")).when(tokenService).deleteRefreshToken(departing.getId());

        // when
        assertThrows(IllegalStateException.class, () -> withdrawalService.withdraw(departing.getId(), Set.of(deleted.getId())));

        // then
        assertTrue(isActive(departing.getId()));
        assertNull(deletedAtOf(departing.getId()));
        assertEquals(departing.getId(), ownerOf(transferred.getId()));
        assertEquals(1, count("SELECT COUNT(*) FROM project WHERE id = ?", deleted.getId()));
        assertEquals(List.of(regular.getId()), membersOf(transferred.getId()));
        assertEquals(List.of(departing.getId()), membersOf(participating.getId()));
        assertEquals("PENDING", statusOf(pending.getId()));
    }

    @Test
    @DisplayName("확인하지 않은 삭제 프로젝트가 있으면 아무것도 변경하지 않고 중단하며 토큰도 삭제하지 않는다")
    void withdraw_UnconfirmedDeletion_LeavesDatabaseUnchanged() {
        // given
        Member departing = member("departing", Role.ROLE_USER);
        Member regular = member("regular", Role.ROLE_USER);
        Member otherOwner = member("other-owner", Role.ROLE_USER);
        Project transferred = project(departing);
        Project deleted = project(departing);
        Project participating = project(otherOwner);
        collaborator(transferred, regular, ProjectCollaboratorRole.EDITOR);
        collaborator(participating, departing, ProjectCollaboratorRole.EDITOR);
        ProjectCollaboratorInvitation pending = invitation(participating, otherOwner, departing);

        // when
        MemberException exception = assertThrows(MemberException.class,
                () -> withdrawalService.withdraw(departing.getId(), Set.of(transferred.getId())));

        // then
        assertEquals(MemberErrorCode.WITHDRAWAL_DELETION_NOT_CONFIRMED, exception.getCode());
        assertTrue(isActive(departing.getId()));
        assertNull(deletedAtOf(departing.getId()));
        assertEquals(departing.getId(), ownerOf(transferred.getId()));
        assertEquals(1, count("SELECT COUNT(*) FROM project WHERE id = ?", deleted.getId()));
        assertEquals(List.of(regular.getId()), membersOf(transferred.getId()));
        assertEquals(List.of(departing.getId()), membersOf(participating.getId()));
        assertEquals("PENDING", statusOf(pending.getId()));
        verify(tokenService, never()).deleteRefreshToken(any());
    }

    @Test
    @DisplayName("서로에게 프로젝트를 승계해야 하는 두 회원이 동시에 탈퇴해도 교착 없이 모두 끝난다")
    void withdraw_ConcurrentMutualSuccession_CompletesWithoutDeadlock() throws Exception {
        // given
        Member first = member("first", Role.ROLE_USER);
        Member second = member("second", Role.ROLE_USER);
        Project firstOwned = project(first);
        Project secondOwned = project(second);
        collaborator(firstOwned, second, ProjectCollaboratorRole.EDITOR);
        collaborator(secondOwned, first, ProjectCollaboratorRole.EDITOR);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // when
            Future<?> firstResult = executor.submit(() -> withdrawAfterStart(first.getId(), Set.of(firstOwned.getId(), secondOwned.getId()), ready, start));
            Future<?> secondResult = executor.submit(() -> withdrawAfterStart(second.getId(), Set.of(firstOwned.getId(), secondOwned.getId()), ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            // then
            assertNotNull(getWithin(firstResult));
            assertNotNull(getWithin(secondResult));
        } finally {
            executor.shutdownNow();
        }
        assertFalse(isActive(first.getId()));
        assertFalse(isActive(second.getId()));
        assertEquals(0, count("SELECT COUNT(*) FROM project_collaborator WHERE 1 = ?", 1L));
        assertEquals(0, count("SELECT COUNT(*) FROM project WHERE member_id IN (?, ?)", first.getId(), second.getId()));
    }

    private Boolean withdrawAfterStart(
            Long memberId, Set<Long> confirmedDeletionProjectIds, CountDownLatch ready, CountDownLatch start
    ) {
        ready.countDown();
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        withdrawalService.withdraw(memberId, confirmedDeletionProjectIds);
        return Boolean.TRUE;
    }

    private Object getWithin(Future<?> future) throws InterruptedException, ExecutionException {
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException exception) {
            throw new AssertionError("동시 탈퇴가 60초 안에 끝나지 않았다(교착 가능성)", exception);
        }
    }

    private Member member(String nickname, Role role) {
        return memberRepository.save(Member.builder().email(nickname + "@test.com").nickname(nickname)
                .password("encodedPassword").role(role).isActive(true).build());
    }

    private Project project(Member owner) {
        return projectRepository.save(Project.builder().title("project").status(ProjectStatus.DRAFT)
                .member(owner).build());
    }

    private void collaborator(Project project, Member member, ProjectCollaboratorRole role) {
        collaboratorRepository.save(ProjectCollaborator.builder().project(project).member(member).role(role).build());
    }

    private ProjectCollaboratorInvitation invitation(Project project, Member inviter, Member invitee) {
        return invitationRepository.save(ProjectCollaboratorInvitation.builder().project(project)
                .invitedBy(inviter).invitee(invitee).role(ProjectCollaboratorRole.EDITOR)
                .expiresAt(LocalDateTime.now().plusDays(1)).build());
    }

    private boolean isActive(Long memberId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT is_active FROM member WHERE id = ?", Boolean.class, memberId));
    }

    private java.time.LocalDateTime deletedAtOf(Long memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM member WHERE id = ?", java.time.LocalDateTime.class, memberId);
    }

    private Long ownerOf(Long projectId) {
        return jdbcTemplate.queryForObject("SELECT member_id FROM project WHERE id = ?", Long.class, projectId);
    }

    private List<Long> membersOf(Long projectId) {
        return jdbcTemplate.queryForList(
                "SELECT member_id FROM project_collaborator WHERE project_id = ? ORDER BY member_id",
                Long.class, projectId);
    }

    private String statusOf(Long invitationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM project_collaborator_invitation WHERE id = ?", String.class, invitationId);
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }
}
