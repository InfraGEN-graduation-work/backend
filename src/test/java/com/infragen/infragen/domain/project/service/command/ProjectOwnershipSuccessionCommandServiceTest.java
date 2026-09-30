package com.infragen.infragen.domain.project.service.command;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.repository.projection.ProjectSuccessionCandidatePreview;
import com.infragen.infragen.domain.project.service.command.ProjectOwnershipSuccessionPlan.Deletion;
import com.infragen.infragen.domain.project.service.command.ProjectOwnershipSuccessionPlan.Transfer;
import java.time.LocalDateTime;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
    @DisplayName("일반 회원 EDITOR가 오래된 일반 회원 VIEWER보다 우선 승계 계획에 선정된다")
    void prepareSuccessionPlans_EditorBeforeOlderViewer_PlansTransferToEditor() {
        // given
        stubLockedProject(PROJECT_ID, OWNER_ID);
        stubCandidates(PROJECT_ID,
                candidate(30L, Role.ROLE_USER, ProjectCollaboratorRole.VIEWER, 100L, JOINED_AT),
                candidate(20L, Role.ROLE_USER, ProjectCollaboratorRole.EDITOR, 200L, JOINED_AT.plusDays(1)));

        // when
        List<ProjectOwnershipSuccessionPlan> plans = service.prepareSuccessionPlans(OWNER_ID, List.of(PROJECT_ID));

        // then
        assertEquals(List.of(new Transfer(PROJECT_ID, OWNER_ID, 20L, Role.ROLE_USER)), plans);
    }

    @ParameterizedTest
    @EnumSource(ProjectCollaboratorRole.class)
    @DisplayName("같은 일반 회원 역할에서는 먼저 참여한 회원을 선정한다")
    void prepareSuccessionPlans_SameRole_PlansEarliestParticipant(ProjectCollaboratorRole role) {
        // given
        stubLockedProject(PROJECT_ID, OWNER_ID);
        stubCandidates(PROJECT_ID,
                candidate(30L, Role.ROLE_USER, role, 100L, JOINED_AT.plusMinutes(1)),
                candidate(20L, Role.ROLE_USER, role, 200L, JOINED_AT));

        // when
        List<ProjectOwnershipSuccessionPlan> plans = service.prepareSuccessionPlans(OWNER_ID, List.of(PROJECT_ID));

        // then
        assertEquals(List.of(new Transfer(PROJECT_ID, OWNER_ID, 20L, Role.ROLE_USER)), plans);
    }

    @Test
    @DisplayName("역할과 참여 시각이 같으면 작은 membership ID의 회원을 선정한다")
    void prepareSuccessionPlans_SameRoleAndTime_PlansSmallestMembershipId() {
        // given
        stubLockedProject(PROJECT_ID, OWNER_ID);
        stubCandidates(PROJECT_ID,
                candidate(20L, Role.ROLE_USER, ProjectCollaboratorRole.EDITOR, 200L, JOINED_AT),
                candidate(30L, Role.ROLE_USER, ProjectCollaboratorRole.EDITOR, 100L, JOINED_AT));

        // when
        List<ProjectOwnershipSuccessionPlan> plans = service.prepareSuccessionPlans(OWNER_ID, List.of(PROJECT_ID));

        // then
        assertEquals(List.of(new Transfer(PROJECT_ID, OWNER_ID, 30L, Role.ROLE_USER)), plans);
    }

    @Test
    @DisplayName("일반 회원 VIEWER가 guest EDITOR보다 우선 선정된다")
    void prepareSuccessionPlans_MixedTypes_PlansRegularViewerBeforeGuestEditor() {
        // given
        stubLockedProject(PROJECT_ID, OWNER_ID);
        stubCandidates(PROJECT_ID,
                candidate(20L, Role.ROLE_GUEST, ProjectCollaboratorRole.EDITOR, 100L, JOINED_AT),
                candidate(30L, Role.ROLE_USER, ProjectCollaboratorRole.VIEWER, 200L, JOINED_AT.plusDays(1)));

        // when
        List<ProjectOwnershipSuccessionPlan> plans = service.prepareSuccessionPlans(OWNER_ID, List.of(PROJECT_ID));

        // then
        assertEquals(List.of(new Transfer(PROJECT_ID, OWNER_ID, 30L, Role.ROLE_USER)), plans);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("일반 회원이 없으면 guest를 무작위로 한 번만 선정해 계획에 고정한다")
    void prepareSuccessionPlans_OnlyGuests_PlansRandomGuestOnce(int index) {
        // given
        stubLockedProject(PROJECT_ID, OWNER_ID);
        List<ProjectSuccessionCandidatePreview> guests = List.of(
                candidate(20L, Role.ROLE_GUEST, ProjectCollaboratorRole.EDITOR, 100L, JOINED_AT),
                candidate(30L, Role.ROLE_GUEST, ProjectCollaboratorRole.VIEWER, 200L, JOINED_AT.plusDays(1)));
        when(collaboratorRepository.findActiveSuccessionCandidatePreviewsByProjectId(PROJECT_ID)).thenReturn(guests);
        ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        when(random.nextInt(2)).thenReturn(index);

        try (MockedStatic<ThreadLocalRandom> randomSource = mockStatic(ThreadLocalRandom.class)) {
            randomSource.when(ThreadLocalRandom::current).thenReturn(random);

            // when
            List<ProjectOwnershipSuccessionPlan> plans =
                    service.prepareSuccessionPlans(OWNER_ID, List.of(PROJECT_ID));

            // then
            assertEquals(List.of(new Transfer(PROJECT_ID, OWNER_ID,
                    guests.get(index).getMemberId(), Role.ROLE_GUEST)), plans);
            verify(random).nextInt(2);
        }
    }

    @Test
    @DisplayName("guest owner가 이용을 종료하면 활성 guest 참여자 중 무작위로 선정한 승계 계획을 만든다")
    void prepareSuccessionPlans_GuestOwnerWithGuestCandidates_PlansRandomGuest() {
        // given
        Project project = project(PROJECT_ID, member(OWNER_ID, Role.ROLE_GUEST, true));
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        List<ProjectSuccessionCandidatePreview> guests = List.of(
                candidate(20L, Role.ROLE_GUEST, ProjectCollaboratorRole.EDITOR, 100L, JOINED_AT),
                candidate(30L, Role.ROLE_GUEST, ProjectCollaboratorRole.VIEWER, 200L, JOINED_AT));
        when(collaboratorRepository.findActiveSuccessionCandidatePreviewsByProjectId(PROJECT_ID)).thenReturn(guests);
        ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        when(random.nextInt(2)).thenReturn(1);

        try (MockedStatic<ThreadLocalRandom> randomSource = mockStatic(ThreadLocalRandom.class)) {
            randomSource.when(ThreadLocalRandom::current).thenReturn(random);

            // when
            List<ProjectOwnershipSuccessionPlan> plans =
                    service.prepareSuccessionPlans(OWNER_ID, List.of(PROJECT_ID));

            // then
            assertEquals(List.of(new Transfer(PROJECT_ID, OWNER_ID, 30L, Role.ROLE_GUEST)), plans);
        }
    }

    @Test
    @DisplayName("승계 후보가 없으면 삭제 계획을 만든다")
    void prepareSuccessionPlans_NoCandidates_PlansDeletion() {
        // given
        stubLockedProject(PROJECT_ID, OWNER_ID);
        stubCandidates(PROJECT_ID);

        // when
        List<ProjectOwnershipSuccessionPlan> plans = service.prepareSuccessionPlans(OWNER_ID, List.of(PROJECT_ID));

        // then
        assertEquals(List.of(new Deletion(PROJECT_ID, OWNER_ID)), plans);
    }

    @Test
    @DisplayName("후보 목록에 떠나는 회원만 있으면 삭제 계획을 만든다")
    void prepareSuccessionPlans_OnlyDepartingMember_PlansDeletion() {
        // given
        stubLockedProject(PROJECT_ID, OWNER_ID);
        stubCandidates(PROJECT_ID,
                candidate(OWNER_ID, Role.ROLE_USER, ProjectCollaboratorRole.EDITOR, 100L, JOINED_AT));

        // when
        List<ProjectOwnershipSuccessionPlan> plans = service.prepareSuccessionPlans(OWNER_ID, List.of(PROJECT_ID));

        // then
        assertEquals(List.of(new Deletion(PROJECT_ID, OWNER_ID)), plans);
    }

    @Test
    @DisplayName("잠금 전에 삭제됐거나 owner가 바뀐 프로젝트는 계획에서 제외한다")
    void prepareSuccessionPlans_ProjectMissingOrOwnerChanged_SkipsProject() {
        // given
        when(projectRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());
        stubLockedProject(20L, 99L);

        // when
        List<ProjectOwnershipSuccessionPlan> plans = service.prepareSuccessionPlans(OWNER_ID, List.of(10L, 20L));

        // then
        assertTrue(plans.isEmpty());
        verifyNoInteractions(collaboratorRepository);
    }

    @Test
    @DisplayName("여러 프로젝트의 계획은 입력 순서를 따르며 준비 단계에서는 회원 잠금이나 변경을 하지 않는다")
    void prepareSuccessionPlans_MixedProjects_KeepsOrderWithoutSideEffects() {
        // given
        Project first = stubLockedProject(10L, OWNER_ID);
        stubLockedProject(20L, OWNER_ID);
        stubCandidates(10L, candidate(40L, Role.ROLE_USER, ProjectCollaboratorRole.EDITOR, 100L, JOINED_AT));
        stubCandidates(20L);

        // when
        List<ProjectOwnershipSuccessionPlan> plans = service.prepareSuccessionPlans(OWNER_ID, List.of(10L, 20L));

        // then
        assertEquals(List.of(
                new Transfer(10L, OWNER_ID, 40L, Role.ROLE_USER),
                new Deletion(20L, OWNER_ID)), plans);
        assertEquals(OWNER_ID, first.getMember().getId());
        assertThrows(UnsupportedOperationException.class, () -> plans.add(new Deletion(30L, OWNER_ID)));
        verifyNoInteractions(memberRepository, projectCommandService);
        verify(collaboratorRepository, never()).deleteByProjectIdAndMemberId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("승계 계획은 project와 선정 회원을 다시 확인한 뒤 참여 기록을 지우고 owner를 바꾼다")
    void executeSuccessionPlans_Transfer_ChangesOwnerWithoutKeepingDepartingMember() {
        // given
        Project project = stubLockedProject(PROJECT_ID, OWNER_ID);
        Member successor = member(20L, Role.ROLE_USER, true);
        when(memberRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(successor));
        when(collaboratorRepository.deleteByProjectIdAndMemberId(PROJECT_ID, 20L)).thenReturn(1L);

        // when
        service.executeSuccessionPlans(List.of(new Transfer(PROJECT_ID, OWNER_ID, 20L, Role.ROLE_USER)));

        // then
        assertSame(successor, project.getMember());
        InOrder order = inOrder(projectRepository, memberRepository, collaboratorRepository);
        order.verify(projectRepository).findByIdForUpdate(PROJECT_ID);
        order.verify(memberRepository).findByIdForUpdate(20L);
        order.verify(collaboratorRepository).deleteByProjectIdAndMemberId(PROJECT_ID, 20L);
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
        verify(collaboratorRepository, never()).findActiveSuccessionCandidatePreviewsByProjectId(anyLong());
        verifyNoInteractions(projectCommandService);
    }

    @Test
    @DisplayName("삭제 계획은 기존 프로젝트 삭제 경로에 위임한다")
    void executeSuccessionPlans_Deletion_DelegatesToDeleteProject() {
        // when
        service.executeSuccessionPlans(List.of(new Deletion(PROJECT_ID, OWNER_ID)));

        // then
        verify(projectCommandService).deleteProject(PROJECT_ID, OWNER_ID);
        verifyNoInteractions(projectRepository, memberRepository, collaboratorRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("선정 회원이 실행 시점에 없거나 비활성이면 재선정 없이 중단한다")
    void executeSuccessionPlans_SuccessorUnavailable_ThrowsTargetUnavailable(boolean missing) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(PROJECT_ID, owner);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(memberRepository.findByIdForUpdate(20L))
                .thenReturn(missing ? Optional.empty() : Optional.of(member(20L, Role.ROLE_USER, false)));

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> service.executeSuccessionPlans(
                List.of(new Transfer(PROJECT_ID, OWNER_ID, 20L, Role.ROLE_USER))));

        // then
        assertEquals(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE, exception.getCode());
        assertSame(owner, project.getMember());
        verify(collaboratorRepository, never()).deleteByProjectIdAndMemberId(anyLong(), anyLong());
        verify(collaboratorRepository, never()).findActiveSuccessionCandidatePreviewsByProjectId(anyLong());
        verifyNoInteractions(projectCommandService);
    }

    @Test
    @DisplayName("선정 회원의 계정 유형이 계획과 다르면 중단한다")
    void executeSuccessionPlans_SuccessorTypeChanged_ThrowsTargetUnavailable() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(PROJECT_ID, owner);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(memberRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(member(20L, Role.ROLE_GUEST, true)));

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> service.executeSuccessionPlans(
                List.of(new Transfer(PROJECT_ID, OWNER_ID, 20L, Role.ROLE_USER))));

        // then
        assertEquals(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE, exception.getCode());
        assertSame(owner, project.getMember());
        verify(collaboratorRepository, never()).deleteByProjectIdAndMemberId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("선정 회원의 참여 기록이 사라졌으면 owner를 바꾸지 않고 중단한다")
    void executeSuccessionPlans_MembershipDisappeared_ThrowsTargetUnavailable() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(PROJECT_ID, owner);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(memberRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(member(20L, Role.ROLE_USER, true)));
        when(collaboratorRepository.deleteByProjectIdAndMemberId(PROJECT_ID, 20L)).thenReturn(0L);

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> service.executeSuccessionPlans(
                List.of(new Transfer(PROJECT_ID, OWNER_ID, 20L, Role.ROLE_USER))));

        // then
        assertEquals(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE, exception.getCode());
        assertSame(owner, project.getMember());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("승계할 프로젝트가 없거나 owner가 계획과 다르면 중단한다")
    void executeSuccessionPlans_ProjectMissingOrOwnerChanged_ThrowsProjectNotFound(boolean missing) {
        // given
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(missing
                ? Optional.empty()
                : Optional.of(project(PROJECT_ID, member(99L, Role.ROLE_USER, true))));

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> service.executeSuccessionPlans(
                List.of(new Transfer(PROJECT_ID, OWNER_ID, 20L, Role.ROLE_USER))));

        // then
        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verifyNoInteractions(memberRepository);
        verify(collaboratorRepository, never()).deleteByProjectIdAndMemberId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("여러 계획은 순서대로 실행하고 중간 실패 시 예외를 전파해 이후 계획을 실행하지 않는다")
    void executeSuccessionPlans_DeleteFails_PropagatesAndStopsProcessing() {
        // given
        Project first = stubLockedProject(10L, OWNER_ID);
        Member successor = member(40L, Role.ROLE_USER, true);
        when(memberRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(successor));
        when(collaboratorRepository.deleteByProjectIdAndMemberId(10L, 40L)).thenReturn(1L);
        ProjectException failure = new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND);
        doThrow(failure).when(projectCommandService).deleteProject(20L, OWNER_ID);

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> service.executeSuccessionPlans(
                List.of(new Transfer(10L, OWNER_ID, 40L, Role.ROLE_USER),
                        new Deletion(20L, OWNER_ID),
                        new Transfer(30L, OWNER_ID, 50L, Role.ROLE_USER))));

        // then
        assertSame(failure, exception);
        assertSame(successor, first.getMember());
        InOrder order = inOrder(collaboratorRepository, projectCommandService);
        order.verify(collaboratorRepository).deleteByProjectIdAndMemberId(10L, 40L);
        order.verify(projectCommandService).deleteProject(20L, OWNER_ID);
        verify(projectRepository, never()).findByIdForUpdate(30L);
        verify(memberRepository, never()).findByIdForUpdate(50L);
    }

    private Project stubLockedProject(Long projectId, Long ownerId) {
        Project project = project(projectId, member(ownerId, Role.ROLE_USER, true));
        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        return project;
    }

    private void stubCandidates(Long projectId, ProjectSuccessionCandidatePreview... candidates) {
        when(collaboratorRepository.findActiveSuccessionCandidatePreviewsByProjectId(projectId))
                .thenReturn(List.of(candidates));
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

    private ProjectSuccessionCandidatePreview candidate(
            Long memberId, Role memberRole, ProjectCollaboratorRole collaboratorRole,
            Long membershipId, LocalDateTime joinedAt
    ) {
        return new CandidatePreview(membershipId, memberId, memberRole, collaboratorRole, joinedAt);
    }

    private record CandidatePreview(
            Long membershipId, Long memberId, Role memberRole,
            ProjectCollaboratorRole collaboratorRole, LocalDateTime joinedAt
    ) implements ProjectSuccessionCandidatePreview {
        @Override
        public Long getMembershipId() {
            return membershipId;
        }

        @Override
        public Long getMemberId() {
            return memberId;
        }

        @Override
        public Role getMemberRole() {
            return memberRole;
        }

        @Override
        public ProjectCollaboratorRole getCollaboratorRole() {
            return collaboratorRole;
        }

        @Override
        public LocalDateTime getJoinedAt() {
            return joinedAt;
        }
    }
}
