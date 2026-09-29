package com.infragen.infragen.domain.project.repository;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.entity.GeneratedFile;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.entity.ProjectCollaboratorInvitation;
import com.infragen.infragen.domain.project.entity.ProjectHistory;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.repository.projection.ProjectSuccessionCandidatePreview;
import com.infragen.infragen.global.enums.ComponentType;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 격리된 MySQL에서 탈퇴용 참여 범위 조회와 회원별 membership 삭제·캐시 정리를 검증한다. */
@Testcontainers
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.docker.compose.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProjectCollaboratorRepositoryTest {
    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse(
            "mysql:8.4.6@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9")
            .asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("infragen_membership_test")
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
    private ProjectCollaboratorRepository repository;

    @Test
    @DisplayName("참여 project ID는 지정 회원의 membership만 project ID 순으로 반환하고 Entity를 적재하지 않는다")
    void findParticipatingProjectIds_MemberScope_ReturnsSortedScalarIds() {
        // given
        Member owner = member("owner");
        Member departingMember = member("departing");
        Member otherMember = member("other");
        Project first = project(owner);
        Project second = project(owner);
        Project unrelated = project(owner);
        project(departingMember);
        membership(second, departingMember);
        membership(first, departingMember);
        membership(first, otherMember);
        membership(unrelated, otherMember);
        flushAndClear();

        // when
        List<Long> projectIds = repository.findParticipatingProjectIdsOrderByProjectIdAsc(departingMember.getId());

        // then
        assertEquals(List.of(first.getId(), second.getId()), projectIds);
        assertEquals(0, entityManager.unwrap(Session.class).getStatistics().getEntityCount());
    }

    @Test
    @DisplayName("참여 membership이 없으면 소유 project가 있어도 빈 목록을 반환한다")
    void findParticipatingProjectIds_NoMembership_ReturnsEmpty() {
        // given
        Member owner = member("owner");
        project(owner);
        flushAndClear();

        // when
        List<Long> projectIds = repository.findParticipatingProjectIdsOrderByProjectIdAsc(owner.getId());

        // then
        assertTrue(projectIds.isEmpty());
    }

    @Test
    @DisplayName("비활성 회원에게 남아 있는 membership도 참여 범위에서 누락하지 않는다")
    void findParticipatingProjectIds_InactiveMember_ReturnsRemainingRelations() {
        // given
        Member owner = member("owner");
        Member departingMember = member("departing");
        Project project = project(owner);
        membership(project, departingMember);
        departingMember.withdraw();
        flushAndClear();

        // when
        List<Long> projectIds = repository.findParticipatingProjectIdsOrderByProjectIdAsc(departingMember.getId());

        // then
        assertEquals(List.of(project.getId()), projectIds);
        assertNull(entityManager.find(Member.class, departingMember.getId()));
    }

    @Test
    @DisplayName("회원의 여러 membership만 삭제하고 타 회원·프로젝트·설계·이력·파일·초대는 보존한다")
    void deleteAllByMemberId_MultipleProjects_PreservesOtherData() {
        // given
        Member owner = member("owner");
        Member departingMember = member("departing");
        Member otherMember = member("other");
        Project first = project(owner);
        Project second = project(owner);
        Project owned = project(departingMember);
        ProjectCollaborator firstMembership = membership(first, departingMember);
        ProjectCollaborator secondMembership = membership(second, departingMember);
        secondMembership.changeRole(ProjectCollaboratorRole.VIEWER);
        ProjectCollaborator otherMembership = membership(first, otherMember);
        ProjectNode node = ProjectNode.builder().project(first).nodeId("mysql-node").nodeName("MySQL")
                .componentType(ComponentType.MYSQL).build();
        entityManager.persist(node);
        ProjectHistory history = ProjectHistory.builder().project(first).versionName("v1")
                .actorMemberId(departingMember.getId()).build();
        entityManager.persist(history);
        GeneratedFile file = GeneratedFile.builder().projectHistory(history).fileName("compose.yaml")
                .filePath("compose.yaml").content("services: {}").build();
        entityManager.persist(file);
        ProjectCollaboratorInvitation invitation = ProjectCollaboratorInvitation.builder()
                .project(first).invitedBy(owner).invitee(departingMember).role(ProjectCollaboratorRole.EDITOR)
                .expiresAt(LocalDateTime.of(2026, 9, 30, 12, 0)).build();
        entityManager.persist(invitation);
        flushAndClear();

        // when
        int deleted = repository.deleteAllByMemberId(departingMember.getId());

        // then
        assertAll(
                () -> assertEquals(2, deleted),
                () -> assertNull(entityManager.find(ProjectCollaborator.class, firstMembership.getId())),
                () -> assertNull(entityManager.find(ProjectCollaborator.class, secondMembership.getId())),
                () -> assertNotNull(entityManager.find(ProjectCollaborator.class, otherMembership.getId())),
                () -> assertEquals(owner.getId(), entityManager.find(Project.class, first.getId()).getMember().getId()),
                () -> assertNotNull(entityManager.find(Project.class, second.getId())),
                () -> assertEquals(departingMember.getId(), entityManager.find(Project.class, owned.getId()).getMember().getId()),
                () -> assertTrue(entityManager.find(Member.class, departingMember.getId()).getIsActive()),
                () -> assertNotNull(entityManager.find(Member.class, owner.getId())),
                () -> assertNotNull(entityManager.find(Member.class, otherMember.getId())),
                () -> assertEquals("MySQL", entityManager.find(ProjectNode.class, node.getId()).getNodeName()),
                () -> assertEquals("v1", entityManager.find(ProjectHistory.class, history.getId()).getVersionName()),
                () -> assertEquals("services: {}", entityManager.find(GeneratedFile.class, file.getId()).getContent()),
                () -> assertNotNull(entityManager.find(ProjectCollaboratorInvitation.class, invitation.getId()))
        );
    }

    @Test
    @DisplayName("membership 삭제를 반복하면 두 번째는 0건이고 다른 회원의 기록은 남는다")
    void deleteAllByMemberId_RepeatedCall_ReturnsZeroAfterFirstDelete() {
        // given
        Member owner = member("owner");
        Member departingMember = member("departing");
        Member otherMember = member("other");
        Project project = project(owner);
        membership(project, departingMember);
        ProjectCollaborator otherMembership = membership(project, otherMember);
        flushAndClear();

        // when
        int firstDeleted = repository.deleteAllByMemberId(departingMember.getId());
        int secondDeleted = repository.deleteAllByMemberId(departingMember.getId());

        // then
        assertEquals(1, firstDeleted);
        assertEquals(0, secondDeleted);
        assertNotNull(entityManager.find(ProjectCollaborator.class, otherMembership.getId()));
    }

    @Test
    @DisplayName("비활성 회원의 남은 membership도 제거한다")
    void deleteAllByMemberId_InactiveMember_RemovesRemainingMembership() {
        // given
        Member owner = member("owner");
        Member departingMember = member("departing");
        Project project = project(owner);
        ProjectCollaborator membership = membership(project, departingMember);
        departingMember.withdraw();
        flushAndClear();

        // when
        int deleted = repository.deleteAllByMemberId(departingMember.getId());

        // then
        assertEquals(1, deleted);
        assertNull(entityManager.find(ProjectCollaborator.class, membership.getId()));
        assertNotNull(entityManager.find(Project.class, project.getId()));
    }

    @Test
    @DisplayName("bulk 삭제 전에 미저장 변경을 반영하고 삭제 후 관리 중인 Entity를 비운다")
    void deleteAllByMemberId_PendingChanges_FlushesBeforeClearing() {
        // given
        Member owner = member("owner");
        Member departingMember = member("departing");
        Member otherMember = member("other");
        Project project = project(owner);
        ProjectCollaborator departingMembership = membership(project, departingMember);
        ProjectCollaborator otherMembership = membership(project, otherMember);
        entityManager.flush();
        otherMembership.changeRole(ProjectCollaboratorRole.VIEWER);
        project.updateInfo("changed-title", "changed-description");

        // when
        int deleted = repository.deleteAllByMemberId(departingMember.getId());

        // then
        assertAll(
                () -> assertEquals(1, deleted),
                () -> assertFalse(entityManager.contains(departingMembership)),
                () -> assertFalse(entityManager.contains(otherMembership)),
                () -> assertFalse(entityManager.contains(project)),
                () -> assertFalse(entityManager.contains(departingMember)),
                () -> assertNull(entityManager.find(ProjectCollaborator.class, departingMembership.getId())),
                () -> assertEquals(ProjectCollaboratorRole.VIEWER,
                        entityManager.find(ProjectCollaborator.class, otherMembership.getId()).getRole()),
                () -> assertEquals("changed-title", entityManager.find(Project.class, project.getId()).getTitle())
        );
    }

    @Test
    @DisplayName("승계 후보 preview는 활성 membership의 scalar 값만 반환하고 Member Entity를 적재하지 않는다")
    void findActiveSuccessionCandidatePreviews_ActiveCandidates_ReturnsScalarValuesWithoutMemberEntity() {
        // given
        Member owner = member("owner");
        Member regularCandidate = member("regular", Role.ROLE_USER);
        Member guestCandidate = member("guest", Role.ROLE_GUEST);
        Project project = project(owner);
        ProjectCollaborator regularMembership = membership(project, regularCandidate, ProjectCollaboratorRole.EDITOR);
        ProjectCollaborator guestMembership = membership(project, guestCandidate, ProjectCollaboratorRole.VIEWER);
        flushAndClear();

        // when
        List<ProjectSuccessionCandidatePreview> previews
                = repository.findActiveSuccessionCandidatePreviewsByProjectId(project.getId());

        // then
        assertEquals(2, previews.size());
        assertEquals(0, entityManager.unwrap(Session.class).getStatistics().getEntityCount());
        ProjectSuccessionCandidatePreview regularPreview = previews.stream()
                .filter(preview -> preview.getMemberId().equals(regularCandidate.getId())).findFirst().orElseThrow();
        assertAll(
                () -> assertEquals(regularMembership.getId(), regularPreview.getMembershipId()),
                () -> assertEquals(Role.ROLE_USER, regularPreview.getMemberRole()),
                () -> assertEquals(ProjectCollaboratorRole.EDITOR, regularPreview.getCollaboratorRole()),
                () -> assertNotNull(regularPreview.getJoinedAt())
        );
        ProjectSuccessionCandidatePreview guestPreview = previews.stream()
                .filter(preview -> preview.getMemberId().equals(guestCandidate.getId())).findFirst().orElseThrow();
        assertAll(
                () -> assertEquals(guestMembership.getId(), guestPreview.getMembershipId()),
                () -> assertEquals(Role.ROLE_GUEST, guestPreview.getMemberRole()),
                () -> assertEquals(ProjectCollaboratorRole.VIEWER, guestPreview.getCollaboratorRole())
        );
    }

    @Test
    @DisplayName("승계 후보 preview는 비활성 회원의 membership을 제외한다")
    void findActiveSuccessionCandidatePreviews_InactiveMember_ExcludesCandidate() {
        // given
        Member owner = member("owner");
        Member activeCandidate = member("active");
        Member inactiveCandidate = member("inactive");
        Project project = project(owner);
        membership(project, activeCandidate);
        membership(project, inactiveCandidate);
        inactiveCandidate.withdraw();
        flushAndClear();

        // when
        List<ProjectSuccessionCandidatePreview> previews
                = repository.findActiveSuccessionCandidatePreviewsByProjectId(project.getId());

        // then
        assertEquals(1, previews.size());
        assertEquals(activeCandidate.getId(), previews.get(0).getMemberId());
    }

    @Test
    @DisplayName("승계 후보 preview는 지정한 project의 membership만 반환한다")
    void findActiveSuccessionCandidatePreviews_OtherProject_ExcludesUnrelatedCandidate() {
        // given
        Member owner = member("owner");
        Member candidate = member("candidate");
        Member unrelatedCandidate = member("unrelated");
        Project project = project(owner);
        Project otherProject = project(owner);
        membership(project, candidate);
        membership(otherProject, unrelatedCandidate);
        flushAndClear();

        // when
        List<ProjectSuccessionCandidatePreview> previews
                = repository.findActiveSuccessionCandidatePreviewsByProjectId(project.getId());

        // then
        assertEquals(1, previews.size());
        assertEquals(candidate.getId(), previews.get(0).getMemberId());
    }

    private Member member(String nickname) {
        return member(nickname, Role.ROLE_USER);
    }

    private Member member(String nickname, Role role) {
        Member member = Member.builder().email(UUID.randomUUID() + "@test.com").nickname(nickname)
                .password("encodedPassword").role(role).isActive(true).build();
        entityManager.persist(member);
        return member;
    }

    private Project project(Member owner) {
        Project project = Project.builder().title("project").status(ProjectStatus.DRAFT).member(owner).build();
        entityManager.persist(project);
        return project;
    }

    private ProjectCollaborator membership(Project project, Member member) {
        return membership(project, member, ProjectCollaboratorRole.EDITOR);
    }

    private ProjectCollaborator membership(Project project, Member member, ProjectCollaboratorRole role) {
        ProjectCollaborator membership = ProjectCollaborator.builder().project(project).member(member)
                .role(role).build();
        entityManager.persist(membership);
        return membership;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
