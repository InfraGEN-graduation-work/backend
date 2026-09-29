package com.infragen.infragen.domain.project.service.command;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectOwnershipSuccessionCommandServiceTest {
    private static final Long OWNER_ID = 1L;
    private static final Long PROJECT_ID = 10L;
    private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectCollaboratorRepository collaboratorRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ProjectCommandService projectCommandService;
    @InjectMocks
    private ProjectOwnershipSuccessionCommandService service;

    @Test
    @DisplayName("일반 회원 EDITOR가 오래된 일반 회원 VIEWER보다 우선 승계한다")
    void succeedOwnedProjectsOnWithdrawal_EditorBeforeOlderViewer_TransfersOwnership() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(PROJECT_ID, owner);
        ProjectCollaborator editor = candidate(project, 20L, Role.ROLE_USER,
                ProjectCollaboratorRole.EDITOR, 200L, JOINED_AT.plusDays(1), true);
        ProjectCollaborator viewer = candidate(project, 30L, Role.ROLE_USER,
                ProjectCollaboratorRole.VIEWER, 100L, JOINED_AT, true);
        stubOwnedProjects(owner, project);
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(PROJECT_ID))
                .thenReturn(List.of(viewer, editor));
        stubSelectedCandidate(editor);

        // when
        service.succeedOwnedProjectsOnWithdrawal(OWNER_ID);

        // then
        assertSame(editor.getMember(), project.getMember());
        verifyTransferOrder(editor);
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
        verifyNoInteractions(projectCommandService);
    }

    @ParameterizedTest
    @EnumSource(ProjectCollaboratorRole.class)
    @DisplayName("같은 일반 회원 역할에서는 먼저 참여한 회원에게 승계한다")
    void succeedOwnedProjectsOnWithdrawal_SameRole_SelectsEarliestParticipant(ProjectCollaboratorRole role) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(PROJECT_ID, owner);
        ProjectCollaborator earlier = candidate(project, 20L, Role.ROLE_USER, role,
                200L, JOINED_AT, true);
        ProjectCollaborator later = candidate(project, 30L, Role.ROLE_USER, role,
                100L, JOINED_AT.plusMinutes(1), true);
        stubOwnedProjects(owner, project);
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(PROJECT_ID))
                .thenReturn(List.of(later, earlier));
        stubSelectedCandidate(earlier);

        // when
        service.succeedOwnedProjectsOnWithdrawal(OWNER_ID);

        // then
        assertSame(earlier.getMember(), project.getMember());
        verify(collaboratorRepository).deleteByProjectIdAndMemberId(PROJECT_ID, 20L);
        verifyNoInteractions(projectCommandService);
    }

    @Test
    @DisplayName("역할과 참여 시각이 같으면 작은 membership ID로 승계 대상을 고정한다")
    void succeedOwnedProjectsOnWithdrawal_SameRoleAndTime_SelectsSmallestMembershipId() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(PROJECT_ID, owner);
        ProjectCollaborator first = candidate(project, 30L, Role.ROLE_USER,
                ProjectCollaboratorRole.EDITOR, 100L, JOINED_AT, true);
        ProjectCollaborator second = candidate(project, 20L, Role.ROLE_USER,
                ProjectCollaboratorRole.EDITOR, 200L, JOINED_AT, true);
        stubOwnedProjects(owner, project);
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(PROJECT_ID))
                .thenReturn(List.of(second, first));
        stubSelectedCandidate(first);

        // when
        service.succeedOwnedProjectsOnWithdrawal(OWNER_ID);

        // then
        assertSame(first.getMember(), project.getMember());
        verify(collaboratorRepository).deleteByProjectIdAndMemberId(PROJECT_ID, 30L);
    }

    @Test
    @DisplayName("일반 회원 VIEWER가 guest EDITOR보다 우선 승계한다")
    void succeedOwnedProjectsOnWithdrawal_MixedTypes_SelectsRegularViewerBeforeGuestEditor() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(PROJECT_ID, owner);
        ProjectCollaborator guest = candidate(project, 20L, Role.ROLE_GUEST,
                ProjectCollaboratorRole.EDITOR, 100L, JOINED_AT, true);
        ProjectCollaborator regular = candidate(project, 30L, Role.ROLE_USER,
                ProjectCollaboratorRole.VIEWER, 200L, JOINED_AT.plusDays(1), true);
        stubOwnedProjects(owner, project);
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(PROJECT_ID))
                .thenReturn(List.of(guest, regular));
        stubSelectedCandidate(regular);

        // when
        service.succeedOwnedProjectsOnWithdrawal(OWNER_ID);

        // then
        assertSame(regular.getMember(), project.getMember());
        verify(memberRepository, never()).findByIdForUpdate(20L);
        verifyNoInteractions(projectCommandService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("일반 회원이 없으면 guest의 역할과 참여 시각 대신 무작위로 승계한다")
    void succeedOwnedProjectsOnWithdrawal_OnlyGuests_SelectsRandomGuest(int index) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(PROJECT_ID, owner);
        ProjectCollaborator editor = candidate(project, 20L, Role.ROLE_GUEST,
                ProjectCollaboratorRole.EDITOR, 100L, JOINED_AT, true);
        ProjectCollaborator viewer = candidate(project, 30L, Role.ROLE_GUEST,
                ProjectCollaboratorRole.VIEWER, 200L, JOINED_AT.plusDays(1), true);
        List<ProjectCollaborator> guests = List.of(editor, viewer);
        stubOwnedProjects(owner, project);
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(PROJECT_ID))
                .thenReturn(guests);
        stubSelectedCandidate(guests.get(index));
        ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        when(random.nextInt(2)).thenReturn(index);

        try (MockedStatic<ThreadLocalRandom> randomSource = mockStatic(ThreadLocalRandom.class)) {
            randomSource.when(ThreadLocalRandom::current).thenReturn(random);

            // when
            service.succeedOwnedProjectsOnWithdrawal(OWNER_ID);

            // then
            assertSame(guests.get(index).getMember(), project.getMember());
            verify(random).nextInt(2);
            verifyNoInteractions(projectCommandService);
        }
    }

    @Test
    @DisplayName("비활성 일반 회원은 제외하고 활성 guest에게 승계한다")
    void succeedOwnedProjectsOnWithdrawal_InactiveRegularCandidate_SelectsActiveGuest() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(PROJECT_ID, owner);
        ProjectCollaborator inactive = candidate(project, 20L, Role.ROLE_USER,
                ProjectCollaboratorRole.EDITOR, 100L, JOINED_AT, false);
        ProjectCollaborator active = candidate(project, 30L, Role.ROLE_GUEST,
                ProjectCollaboratorRole.VIEWER, 200L, JOINED_AT, true);
        stubOwnedProjects(owner, project);
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(PROJECT_ID))
                .thenReturn(List.of(inactive, active));
        stubSelectedCandidate(active);

        // when
        service.succeedOwnedProjectsOnWithdrawal(OWNER_ID);

        // then
        assertSame(active.getMember(), project.getMember());
        verify(memberRepository, never()).findByIdForUpdate(20L);
    }

    @Test
    @DisplayName("승계 후보가 없으면 기존 프로젝트 삭제 경로에 위임한다")
    void succeedOwnedProjectsOnWithdrawal_NoCandidates_DeletesProject() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(PROJECT_ID, owner);
        stubOwnedProjects(owner, project);
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(PROJECT_ID))
                .thenReturn(List.of());

        // when
        service.succeedOwnedProjectsOnWithdrawal(OWNER_ID);

        // then
        verify(projectCommandService).deleteProject(PROJECT_ID, OWNER_ID);
        verify(memberRepository, never()).findByIdForUpdate(any(Long.class));
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @Test
    @DisplayName("비활성 후보나 떠나는 회원만 남으면 프로젝트를 삭제한다")
    void succeedOwnedProjectsOnWithdrawal_NoEligibleCandidates_DeletesProject() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(PROJECT_ID, owner);
        stubOwnedProjects(owner, project);
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(PROJECT_ID))
                .thenReturn(List.of(
                        candidate(project, 20L, Role.ROLE_USER, ProjectCollaboratorRole.EDITOR,
                                100L, JOINED_AT, false),
                        candidate(project, OWNER_ID, Role.ROLE_USER, ProjectCollaboratorRole.EDITOR,
                                200L, JOINED_AT, true)));

        // when
        service.succeedOwnedProjectsOnWithdrawal(OWNER_ID);

        // then
        verify(projectCommandService).deleteProject(PROJECT_ID, OWNER_ID);
        verify(memberRepository, never()).findByIdForUpdate(any(Long.class));
    }

    @Test
    @DisplayName("소유 프로젝트가 없으면 승계나 삭제를 실행하지 않는다")
    void succeedOwnedProjectsOnWithdrawal_NoOwnedProjects_DoesNothing() {
        // given
        when(memberRepository.findById(OWNER_ID))
                .thenReturn(Optional.of(member(OWNER_ID, Role.ROLE_USER, true)));
        when(projectRepository.findOwnedProjectIdsOrderByIdAsc(OWNER_ID)).thenReturn(List.of());

        // when
        service.succeedOwnedProjectsOnWithdrawal(OWNER_ID);

        // then
        verify(projectRepository, never()).findByIdForUpdate(any(Long.class));
        verifyNoInteractions(collaboratorRepository, projectCommandService);
    }

    @Test
    @DisplayName("존재하지 않는 회원의 프로젝트 처리를 시작하지 않는다")
    void succeedOwnedProjectsOnWithdrawal_MemberNotFound_ThrowsMemberNotFound() {
        // given
        when(memberRepository.findById(OWNER_ID)).thenReturn(Optional.empty());

        // when
        MemberException exception = assertThrows(MemberException.class,
                () -> service.succeedOwnedProjectsOnWithdrawal(OWNER_ID));

        // then
        assertEquals(MemberErrorCode.MEMBER_NOT_FOUND, exception.getCode());
        verifyNoInteractions(projectRepository, collaboratorRepository, projectCommandService);
    }

    @Test
    @DisplayName("캐시에 비활성 회원이 있어도 프로젝트 처리를 시작하지 않는다")
    void succeedOwnedProjectsOnWithdrawal_InactiveMember_ThrowsMemberNotFound() {
        // given
        when(memberRepository.findById(OWNER_ID))
                .thenReturn(Optional.of(member(OWNER_ID, Role.ROLE_USER, false)));

        // when
        MemberException exception = assertThrows(MemberException.class,
                () -> service.succeedOwnedProjectsOnWithdrawal(OWNER_ID));

        // then
        assertEquals(MemberErrorCode.MEMBER_NOT_FOUND, exception.getCode());
        verifyNoInteractions(projectRepository, collaboratorRepository, projectCommandService);
    }

    @Test
    @DisplayName("guest는 일반 회원 탈퇴의 승계 정책을 사용할 수 없다")
    void succeedOwnedProjectsOnWithdrawal_GuestMember_ThrowsGuestActionNotAllowed() {
        // given
        when(memberRepository.findById(OWNER_ID))
                .thenReturn(Optional.of(member(OWNER_ID, Role.ROLE_GUEST, true)));

        // when
        MemberException exception = assertThrows(MemberException.class,
                () -> service.succeedOwnedProjectsOnWithdrawal(OWNER_ID));

        // then
        assertEquals(MemberErrorCode.GUEST_ACTION_NOT_ALLOWED, exception.getCode());
        verifyNoInteractions(projectRepository, collaboratorRepository, projectCommandService);
    }

    @Test
    @DisplayName("ID 조회 이후 삭제된 프로젝트는 건너뛴다")
    void succeedOwnedProjectsOnWithdrawal_ProjectDeletedBeforeLock_SkipsProject() {
        // given
        when(memberRepository.findById(OWNER_ID))
                .thenReturn(Optional.of(member(OWNER_ID, Role.ROLE_USER, true)));
        when(projectRepository.findOwnedProjectIdsOrderByIdAsc(OWNER_ID)).thenReturn(List.of(PROJECT_ID));
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.empty());

        // when
        service.succeedOwnedProjectsOnWithdrawal(OWNER_ID);

        // then
        verifyNoInteractions(collaboratorRepository, projectCommandService);
    }

    @Test
    @DisplayName("잠금 이후 소유자가 바뀐 프로젝트는 승계하거나 삭제하지 않는다")
    void succeedOwnedProjectsOnWithdrawal_OwnerChangedBeforeLock_SkipsProject() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(PROJECT_ID, member(99L, Role.ROLE_USER, true));
        stubOwnedProjects(owner, project);

        // when
        service.succeedOwnedProjectsOnWithdrawal(OWNER_ID);

        // then
        assertEquals(99L, project.getMember().getId());
        verifyNoInteractions(collaboratorRepository, projectCommandService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("선정한 회원이 잠금 시점에 비활성이면 이전이나 삭제 대신 처리를 중단한다")
    void succeedOwnedProjectsOnWithdrawal_SuccessorUnavailableAtLock_ThrowsTargetUnavailable(boolean filtered) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(PROJECT_ID, owner);
        ProjectCollaborator candidate = candidate(project, 20L, Role.ROLE_USER,
                ProjectCollaboratorRole.EDITOR, 100L, JOINED_AT, true);
        stubOwnedProjects(owner, project);
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(PROJECT_ID))
                .thenReturn(List.of(candidate));
        when(memberRepository.findByIdForUpdate(20L))
                .thenReturn(filtered ? Optional.empty() : Optional.of(member(20L, Role.ROLE_USER, false)));

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> service.succeedOwnedProjectsOnWithdrawal(OWNER_ID));

        // then
        assertEquals(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE, exception.getCode());
        assertSame(owner, project.getMember());
        verify(collaboratorRepository, never()).deleteByProjectIdAndMemberId(PROJECT_ID, 20L);
        verifyNoInteractions(projectCommandService);
    }

    @Test
    @DisplayName("선정한 회원의 계정 유형이 잠금 조회에서 달라지면 처리를 중단한다")
    void succeedOwnedProjectsOnWithdrawal_SuccessorTypeChanged_ThrowsTargetUnavailable() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(PROJECT_ID, owner);
        ProjectCollaborator candidate = candidate(project, 20L, Role.ROLE_USER,
                ProjectCollaboratorRole.EDITOR, 100L, JOINED_AT, true);
        stubOwnedProjects(owner, project);
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(PROJECT_ID))
                .thenReturn(List.of(candidate));
        when(memberRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(member(20L, Role.ROLE_GUEST, true)));

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> service.succeedOwnedProjectsOnWithdrawal(OWNER_ID));

        // then
        assertEquals(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE, exception.getCode());
        assertSame(owner, project.getMember());
        verify(collaboratorRepository, never()).deleteByProjectIdAndMemberId(PROJECT_ID, 20L);
    }

    @Test
    @DisplayName("선정한 참여 기록 삭제가 실패하면 소유자를 바꾸지 않는다")
    void succeedOwnedProjectsOnWithdrawal_MembershipDisappeared_ThrowsTargetUnavailable() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(PROJECT_ID, owner);
        ProjectCollaborator candidate = candidate(project, 20L, Role.ROLE_USER,
                ProjectCollaboratorRole.EDITOR, 100L, JOINED_AT, true);
        stubOwnedProjects(owner, project);
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(PROJECT_ID))
                .thenReturn(List.of(candidate));
        when(memberRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(candidate.getMember()));
        when(collaboratorRepository.deleteByProjectIdAndMemberId(PROJECT_ID, 20L)).thenReturn(0L);

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> service.succeedOwnedProjectsOnWithdrawal(OWNER_ID));

        // then
        assertEquals(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE, exception.getCode());
        assertSame(owner, project.getMember());
        verifyNoInteractions(projectCommandService);
    }

    @Test
    @DisplayName("여러 프로젝트는 ID 순서대로 다시 조회하며 승계와 삭제를 함께 처리한다")
    void succeedOwnedProjectsOnWithdrawal_MixedProjects_ProcessesInProjectIdOrder() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project first = project(10L, owner);
        Project second = project(20L, owner);
        Project third = project(30L, owner);
        stubOwnedProjects(owner, first, second, third);
        ProjectCollaborator firstCandidate = candidate(first, 40L, Role.ROLE_USER,
                ProjectCollaboratorRole.EDITOR, 100L, JOINED_AT, true);
        ProjectCollaborator thirdCandidate = candidate(third, 50L, Role.ROLE_USER,
                ProjectCollaboratorRole.VIEWER, 200L, JOINED_AT, true);
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(10L))
                .thenReturn(List.of(firstCandidate));
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(20L)).thenReturn(List.of());
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(30L))
                .thenReturn(List.of(thirdCandidate));
        stubSelectedCandidate(firstCandidate);
        stubSelectedCandidate(thirdCandidate);

        // when
        service.succeedOwnedProjectsOnWithdrawal(OWNER_ID);

        // then
        assertSame(firstCandidate.getMember(), first.getMember());
        assertSame(thirdCandidate.getMember(), third.getMember());
        InOrder order = inOrder(projectRepository, collaboratorRepository, memberRepository, projectCommandService);
        order.verify(projectRepository).findByIdForUpdate(10L);
        order.verify(collaboratorRepository).deleteByProjectIdAndMemberId(10L, 40L);
        order.verify(projectRepository).findByIdForUpdate(20L);
        order.verify(projectCommandService).deleteProject(20L, OWNER_ID);
        order.verify(projectRepository).findByIdForUpdate(30L);
        order.verify(collaboratorRepository).deleteByProjectIdAndMemberId(30L, 50L);
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @Test
    @DisplayName("앞선 승계 이후 삭제가 실패해도 예외를 전파하고 후속 처리를 중단한다")
    void succeedOwnedProjectsOnWithdrawal_DeleteFails_PropagatesAndStopsProcessing() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        when(memberRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(projectRepository.findOwnedProjectIdsOrderByIdAsc(OWNER_ID)).thenReturn(List.of(10L, 20L, 30L));
        Project first = project(10L, owner);
        when(projectRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(first));
        ProjectCollaborator candidate = candidate(first, 40L, Role.ROLE_USER,
                ProjectCollaboratorRole.EDITOR, 100L, JOINED_AT, true);
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(10L))
                .thenReturn(List.of(candidate));
        stubSelectedCandidate(candidate);
        when(projectRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(project(20L, owner)));
        when(collaboratorRepository.findActiveSuccessionCandidatesByProjectId(20L)).thenReturn(List.of());
        ProjectException failure = new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND);
        doThrow(failure).when(projectCommandService).deleteProject(20L, OWNER_ID);

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> service.succeedOwnedProjectsOnWithdrawal(OWNER_ID));

        // then
        assertSame(failure, exception);
        verify(collaboratorRepository).deleteByProjectIdAndMemberId(10L, 40L);
        verify(projectRepository, never()).findByIdForUpdate(30L);
    }

    private void stubOwnedProjects(Member owner, Project... projects) {
        when(memberRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(projectRepository.findOwnedProjectIdsOrderByIdAsc(OWNER_ID))
                .thenReturn(Arrays.stream(projects).map(Project::getId).toList());
        for (Project project : projects) {
            when(projectRepository.findByIdForUpdate(project.getId())).thenReturn(Optional.of(project));
        }
    }

    private void stubSelectedCandidate(ProjectCollaborator candidate) {
        Long projectId = candidate.getProject().getId();
        Long memberId = candidate.getMember().getId();
        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(candidate.getMember()));
        when(collaboratorRepository.deleteByProjectIdAndMemberId(projectId, memberId)).thenReturn(1L);
    }

    private void verifyTransferOrder(ProjectCollaborator candidate) {
        InOrder order = inOrder(projectRepository, collaboratorRepository, memberRepository);
        order.verify(projectRepository).findByIdForUpdate(PROJECT_ID);
        order.verify(collaboratorRepository).findActiveSuccessionCandidatesByProjectId(PROJECT_ID);
        order.verify(memberRepository).findByIdForUpdate(candidate.getMember().getId());
        order.verify(collaboratorRepository).deleteByProjectIdAndMemberId(PROJECT_ID, candidate.getMember().getId());
    }

    private Member member(Long id, Role role, boolean active) {
        Member member = Member.builder()
                .email("member-" + id + "@test.com")
                .password("encoded")
                .nickname("member")
                .role(role)
                .isActive(active)
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Project project(Long id, Member owner) {
        Project project = Project.builder().title("project").member(owner).build();
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }

    private ProjectCollaborator candidate(
            Project project, Long memberId, Role type, ProjectCollaboratorRole role,
            Long membershipId, LocalDateTime joinedAt, boolean active
    ) {
        ProjectCollaborator candidate = ProjectCollaborator.builder()
                .project(project)
                .member(member(memberId, type, active))
                .role(role)
                .build();
        ReflectionTestUtils.setField(candidate, "id", membershipId);
        ReflectionTestUtils.setField(candidate, "createdAt", joinedAt);
        return candidate;
    }
}
