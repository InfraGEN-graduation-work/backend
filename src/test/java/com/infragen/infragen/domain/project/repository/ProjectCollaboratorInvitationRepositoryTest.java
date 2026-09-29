package com.infragen.infragen.domain.project.repository;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.converter.ProjectCollaboratorInvitationConverter;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO.InvitationStatus;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaboratorInvitation;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorInvitationStatus;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 격리된 MySQL에서 soft-delete 이력 조회·표시명·scalar 자료형과 정렬을 검증한다. */
@Testcontainers
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.docker.compose.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProjectCollaboratorInvitationRepositoryTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 28, 10, 0, 0, 123456000);
    private static final LocalDateTime RESPONDED_AT = LocalDateTime.of(2026, 9, 28, 11, 0);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 9, 29, 10, 0);

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse(
            "mysql:8.4.6@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9")
            .asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("infragen_invitation_history_test")
            .withUsername("infragen_test").withPassword("infragen_test_password");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ProjectCollaboratorInvitationRepository repository;

    @ParameterizedTest
    @EnumSource(ProjectCollaboratorInvitationStatus.class)
    @DisplayName("탈퇴 대상의 모든 저장 상태·시각은 발신 이력에 남고 이전 닉네임은 표시하지 않는다")
    void findSentHistory_InactiveInvitee_PreservesStoredHistory(ProjectCollaboratorInvitationStatus status) {
        // given
        Member owner = member("owner");
        Member invitee = member("old-invitee-name");
        Project project = project(owner);
        ProjectCollaboratorInvitation invitation = invitation(project, owner, invitee, status, CREATED_AT);
        Long inviteeId = invitee.getId();
        entityManager.remove(invitee);
        flushAndClear();

        // when
        List<Tuple> rows = repository.findSentHistoryByProjectId(project.getId());

        // then
        assertEquals(1, rows.size());
        var item = ProjectCollaboratorInvitationConverter.toSentItem(rows.get(0), InvitationStatus.valueOf(status.name()));
        assertAll(
                () -> assertNull(entityManager.find(Member.class, inviteeId)),
                () -> assertEquals(invitation.getId(), item.invitationId()),
                () -> assertEquals("탈퇴회원", item.inviteeNickname()),
                () -> assertEquals(status.name(), rows.get(0).get("status", String.class)),
                () -> assertEquals(CREATED_AT, item.createdAt()),
                () -> assertEquals(EXPIRES_AT, item.expiresAt()),
                () -> assertEquals(status == ProjectCollaboratorInvitationStatus.PENDING ? null : RESPONDED_AT, item.respondedAt())
        );
    }

    @ParameterizedTest
    @EnumSource(ProjectCollaboratorInvitationStatus.class)
    @DisplayName("탈퇴 초대자의 모든 저장 상태는 본인 수신 이력에 남는다")
    void findReceivedHistory_InactiveInviter_PreservesStoredHistory(ProjectCollaboratorInvitationStatus status) {
        // given
        Member inviter = member("old-inviter-name");
        Member invitee = member("invitee");
        Project project = project(inviter);
        ProjectCollaboratorInvitation invitation = invitation(project, inviter, invitee, status, CREATED_AT);
        Long inviterId = inviter.getId();
        entityManager.remove(inviter);
        flushAndClear();

        // when
        List<Tuple> rows = repository.findReceivedHistoryByInviteeId(invitee.getId());

        // then
        assertEquals(1, rows.size());
        var item = ProjectCollaboratorInvitationConverter.toReceivedItem(rows.get(0), InvitationStatus.valueOf(status.name()));
        assertAll(
                () -> assertNull(entityManager.find(Member.class, inviterId)),
                () -> assertEquals(invitation.getId(), item.invitationId()),
                () -> assertEquals("탈퇴회원", item.inviterNickname()),
                () -> assertEquals(project.getTitle(), item.projectTitle()),
                () -> assertEquals(status.name(), rows.get(0).get("status", String.class)),
                () -> assertEquals(CREATED_AT, item.createdAt()),
                () -> assertEquals(EXPIRES_AT, item.expiresAt())
        );
    }

    @Test
    @DisplayName("탈퇴한 취소 처리자 FK도 이력 조회를 방해하지 않는다")
    void findHistory_InactiveResponder_PreservesCancellationAndForeignKey() {
        // given
        Member owner = member("owner");
        Member invitee = member("invitee");
        Member responder = member("responder");
        Project project = project(owner);
        ProjectCollaboratorInvitation invitation = invitation(project, owner, invitee,
                ProjectCollaboratorInvitationStatus.PENDING, CREATED_AT);
        invitation.cancel(responder, RESPONDED_AT);
        entityManager.remove(responder);
        flushAndClear();

        // when
        List<Tuple> sent = repository.findSentHistoryByProjectId(project.getId());
        List<Tuple> received = repository.findReceivedHistoryByInviteeId(invitee.getId());

        // then
        assertAll(
                () -> assertEquals(1, sent.size()),
                () -> assertEquals(1, received.size()),
                () -> assertEquals(RESPONDED_AT, ProjectCollaboratorInvitationConverter
                        .toSentItem(sent.get(0), InvitationStatus.CANCELLED).respondedAt()),
                () -> assertEquals("CANCELLED", received.get(0).get("status", String.class)),
                () -> assertEquals(responder.getId().longValue(), ((Number) entityManager.createNativeQuery(
                        "SELECT responded_by_member_id FROM project_collaborator_invitation WHERE id = :id", Long.class)
                        .setParameter("id", invitation.getId()).getSingleResult()).longValue())
        );
    }

    @Test
    @DisplayName("발신 목록은 프로젝트를 제한하고 생성 시각·동률 ID 역순을 유지한다")
    void findSentHistory_ProjectScope_ReturnsOrderedHistory() {
        // given
        Member originalOwner = member("original-owner");
        Member newOwner = member("new-owner");
        Member invitee = member("active-invitee");
        Project project = project(originalOwner);
        Project otherProject = project(originalOwner);
        ProjectCollaboratorInvitation oldest = invitation(project, originalOwner, invitee,
                ProjectCollaboratorInvitationStatus.PENDING, CREATED_AT.minusDays(1));
        ProjectCollaboratorInvitation first = invitation(project, originalOwner, invitee,
                ProjectCollaboratorInvitationStatus.ACCEPTED, CREATED_AT);
        ProjectCollaboratorInvitation second = invitation(project, originalOwner, invitee,
                ProjectCollaboratorInvitationStatus.DECLINED, CREATED_AT);
        invitation(otherProject, originalOwner, invitee, ProjectCollaboratorInvitationStatus.PENDING, CREATED_AT.plusDays(1));
        project.transferOwnershipTo(newOwner);
        flushAndClear();

        // when
        List<Tuple> rows = repository.findSentHistoryByProjectId(project.getId());

        // then
        assertAll(
                () -> assertEquals(List.of(second.getId(), first.getId(), oldest.getId()), ids(rows)),
                () -> assertTrue(rows.stream().allMatch(row -> "active-invitee".equals(row.get("memberNickname", String.class))))
        );
    }

    @Test
    @DisplayName("수신 목록은 지정 회원의 여러 프로젝트 이력만 최신순으로 반환한다")
    void findReceivedHistory_InviteeScope_ReturnsOrderedHistoryAcrossProjects() {
        // given
        Member owner = member("active-owner");
        Member invitee = member("invitee");
        Member otherInvitee = member("other-invitee");
        Project firstProject = project(owner);
        Project secondProject = project(owner);
        ProjectCollaboratorInvitation oldest = invitation(firstProject, owner, invitee,
                ProjectCollaboratorInvitationStatus.PENDING, CREATED_AT.minusDays(1));
        ProjectCollaboratorInvitation first = invitation(firstProject, owner, invitee,
                ProjectCollaboratorInvitationStatus.ACCEPTED, CREATED_AT);
        ProjectCollaboratorInvitation second = invitation(secondProject, owner, invitee,
                ProjectCollaboratorInvitationStatus.CANCELLED, CREATED_AT);
        invitation(firstProject, owner, otherInvitee, ProjectCollaboratorInvitationStatus.PENDING, CREATED_AT.plusDays(1));
        flushAndClear();

        // when
        List<Tuple> rows = repository.findReceivedHistoryByInviteeId(invitee.getId());

        // then
        assertAll(
                () -> assertEquals(List.of(second.getId(), first.getId(), oldest.getId()), ids(rows)),
                () -> assertTrue(rows.stream().allMatch(row -> "active-owner".equals(row.get("memberNickname", String.class))))
        );
    }

    private Member member(String nickname) {
        Member member = Member.builder().email(UUID.randomUUID() + "@test.com").nickname(nickname)
                .password("encodedPassword").role(Role.ROLE_USER).isActive(true).build();
        entityManager.persist(member);
        return member;
    }

    private Project project(Member owner) {
        Project project = Project.builder().title("project").status(ProjectStatus.DRAFT).member(owner).build();
        entityManager.persist(project);
        return project;
    }

    private ProjectCollaboratorInvitation invitation(Project project, Member inviter, Member invitee,
                                                    ProjectCollaboratorInvitationStatus status, LocalDateTime createdAt) {
        ProjectCollaboratorInvitation invitation = ProjectCollaboratorInvitation.builder()
                .project(project).invitedBy(inviter).invitee(invitee).role(ProjectCollaboratorRole.EDITOR)
                .expiresAt(EXPIRES_AT).build();
        switch (status) {
            case ACCEPTED -> invitation.accept(invitee, RESPONDED_AT);
            case DECLINED -> invitation.decline(invitee, RESPONDED_AT);
            case CANCELLED -> invitation.cancel(inviter, RESPONDED_AT);
            case PENDING -> { }
        }
        entityManager.persist(invitation);
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE project_collaborator_invitation SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt).setParameter("id", invitation.getId()).executeUpdate();
        return invitation;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private List<Long> ids(List<Tuple> rows) {
        return rows.stream().map(row -> row.get("invitationId", Number.class).longValue()).toList();
    }
}
