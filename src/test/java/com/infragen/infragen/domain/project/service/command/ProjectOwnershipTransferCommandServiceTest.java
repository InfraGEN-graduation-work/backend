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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.PessimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ProjectOwnershipTransferCommandServiceTest {
    private static final Long PROJECT_ID = 10L;
    private static final Long OWNER_ID = 1L;
    private static final Long TARGET_ID = 2L;

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectCollaboratorRepository collaboratorRepository;
    @Mock
    private MemberRepository memberRepository;
    @InjectMocks
    private ProjectOwnershipTransferCommandService service;

    @ParameterizedTest
    @CsvSource({"1, 2, ROLE_USER", "20, 2, ROLE_USER", "1, 2, ROLE_GUEST", "20, 2, ROLE_GUEST"})
    @DisplayName("프로젝트 다음 회원을 ID 순으로 잠그고 이전 owner는 EDITOR로 남긴다")
    void transfer_ActiveCollaborator_ChangesOwnerAndMemberships(Long ownerId, Long targetId, Role role) {
        // given
        Member previousOwner = member(ownerId, role, true);
        Member newOwner = member(targetId, role, true);
        Project project = project(previousOwner);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(collaboratorRepository.existsByProjectIdAndMemberId(PROJECT_ID, targetId)).thenReturn(true);
        when(memberRepository.findByIdForUpdate(ownerId)).thenReturn(Optional.of(previousOwner));
        when(memberRepository.findByIdForUpdate(targetId)).thenReturn(Optional.of(newOwner));
        when(collaboratorRepository.deleteByProjectIdAndMemberId(PROJECT_ID, targetId)).thenReturn(1L);

        // when
        service.transfer(PROJECT_ID, ownerId, targetId);

        // then
        assertSame(newOwner, project.getMember());
        InOrder order = inOrder(projectRepository, memberRepository, collaboratorRepository);
        order.verify(projectRepository).findByIdForUpdate(PROJECT_ID);
        order.verify(memberRepository).findByIdForUpdate(Math.min(ownerId, targetId));
        order.verify(memberRepository).findByIdForUpdate(Math.max(ownerId, targetId));
        order.verify(collaboratorRepository).deleteByProjectIdAndMemberId(PROJECT_ID, targetId);
        ArgumentCaptor<ProjectCollaborator> saved = ArgumentCaptor.forClass(ProjectCollaborator.class);
        order.verify(collaboratorRepository).save(saved.capture());
        assertSame(project, saved.getValue().getProject());
        assertSame(previousOwner, saved.getValue().getMember());
        assertEquals(ProjectCollaboratorRole.EDITOR, saved.getValue().getRole());
    }

    @Test
    @DisplayName("현재 owner가 아니면 소유권을 이전할 수 없다")
    void transfer_NotOwner_ThrowsProjectNotFound() {
        // given
        Project project = project(member(OWNER_ID, Role.ROLE_USER, true));
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> service.transfer(PROJECT_ID, 99L, TARGET_ID));

        // then
        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verifyNoInteractions(collaboratorRepository, memberRepository);
    }

    @Test
    @DisplayName("참여자가 아닌 회원에게는 소유권을 이전할 수 없다")
    void transfer_NonCollaborator_ThrowsTargetUnavailable() {
        // given
        Project project = project(member(OWNER_ID, Role.ROLE_USER, true));
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> service.transfer(PROJECT_ID, OWNER_ID, TARGET_ID));

        // then
        assertEquals(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE, exception.getCode());
        assertEquals(OWNER_ID, project.getMember().getId());
        verifyNoInteractions(memberRepository);
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @Test
    @DisplayName("비활성 회원에게는 소유권을 이전할 수 없다")
    void transfer_InactiveCollaborator_ThrowsTargetUnavailable() {
        // given
        Project project = project(member(OWNER_ID, Role.ROLE_USER, true));
        Member inactiveTarget = member(TARGET_ID, Role.ROLE_USER, false);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(collaboratorRepository.existsByProjectIdAndMemberId(PROJECT_ID, TARGET_ID)).thenReturn(true);
        when(memberRepository.findByIdForUpdate(OWNER_ID)).thenReturn(Optional.of(project.getMember()));
        when(memberRepository.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(inactiveTarget));

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> service.transfer(PROJECT_ID, OWNER_ID, TARGET_ID));

        // then
        assertEquals(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE, exception.getCode());
        assertEquals(OWNER_ID, project.getMember().getId());
        verify(collaboratorRepository, never()).deleteByProjectIdAndMemberId(PROJECT_ID, TARGET_ID);
    }

    @Test
    @DisplayName("비활성 회원 조회가 제외되면 소유권을 이전하지 않는다")
    void transfer_TargetFilteredFromMemberLookup_ThrowsTargetUnavailable() {
        // given
        Project project = project(member(OWNER_ID, Role.ROLE_USER, true));
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(collaboratorRepository.existsByProjectIdAndMemberId(PROJECT_ID, TARGET_ID)).thenReturn(true);
        when(memberRepository.findByIdForUpdate(OWNER_ID)).thenReturn(Optional.of(project.getMember()));
        when(memberRepository.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.empty());

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> service.transfer(PROJECT_ID, OWNER_ID, TARGET_ID));

        // then
        assertEquals(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE, exception.getCode());
        assertEquals(OWNER_ID, project.getMember().getId());
        verify(collaboratorRepository, never()).deleteByProjectIdAndMemberId(PROJECT_ID, TARGET_ID);
    }

    @Test
    @DisplayName("수동 이전에서는 현재 owner와 계정 유형이 다른 대상은 거부한다")
    void transfer_CrossTypeCollaborator_ThrowsTargetUnavailable() {
        // given
        Project project = project(member(OWNER_ID, Role.ROLE_USER, true));
        Member guestTarget = member(TARGET_ID, Role.ROLE_GUEST, true);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(collaboratorRepository.existsByProjectIdAndMemberId(PROJECT_ID, TARGET_ID)).thenReturn(true);
        when(memberRepository.findByIdForUpdate(OWNER_ID)).thenReturn(Optional.of(project.getMember()));
        when(memberRepository.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(guestTarget));

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> service.transfer(PROJECT_ID, OWNER_ID, TARGET_ID));

        // then
        assertEquals(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE, exception.getCode());
        assertEquals(OWNER_ID, project.getMember().getId());
        verify(collaboratorRepository, never()).deleteByProjectIdAndMemberId(PROJECT_ID, TARGET_ID);
    }

    @Test
    @DisplayName("대상 참여 기록이 경쟁 요청으로 사라졌다면 owner는 바뀌지 않는다")
    void transfer_TargetMembershipDisappeared_ThrowsTargetUnavailable() {
        // given
        Member previousOwner = member(OWNER_ID, Role.ROLE_USER, true);
        Member newOwner = member(TARGET_ID, Role.ROLE_USER, true);
        Project project = project(previousOwner);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(collaboratorRepository.existsByProjectIdAndMemberId(PROJECT_ID, TARGET_ID)).thenReturn(true);
        when(memberRepository.findByIdForUpdate(OWNER_ID)).thenReturn(Optional.of(project.getMember()));
        when(memberRepository.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(newOwner));

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> service.transfer(PROJECT_ID, OWNER_ID, TARGET_ID));

        // then
        assertEquals(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE, exception.getCode());
        assertSame(previousOwner, project.getMember());
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("현재 owner의 잠금 조회가 부재·비활성이면 이전을 거부한다")
    void transfer_UnavailableLockedOwner_ThrowsProjectNotFound(boolean missing) {
        // given
        Member previousOwner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(previousOwner);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(collaboratorRepository.existsByProjectIdAndMemberId(PROJECT_ID, TARGET_ID)).thenReturn(true);
        when(memberRepository.findByIdForUpdate(OWNER_ID)).thenReturn(missing ? Optional.empty()
                : Optional.of(member(OWNER_ID, Role.ROLE_USER, false)));

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> service.transfer(PROJECT_ID, OWNER_ID, TARGET_ID));

        // then
        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        assertSame(previousOwner, project.getMember());
        verify(memberRepository, never()).findByIdForUpdate(TARGET_ID);
        verify(collaboratorRepository, never()).deleteByProjectIdAndMemberId(PROJECT_ID, TARGET_ID);
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2L})
    @DisplayName("회원 잠금 실패를 전파하고 owner·membership을 변경하지 않는다")
    void transfer_MemberLockFailure_PropagatesWithoutMutation(Long failingId) {
        // given
        Member previousOwner = member(OWNER_ID, Role.ROLE_USER, true);
        Project project = project(previousOwner);
        var failure = new PessimisticLockingFailureException("member lock failed");
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(collaboratorRepository.existsByProjectIdAndMemberId(PROJECT_ID, TARGET_ID)).thenReturn(true);
        if (failingId.equals(TARGET_ID)) {
            when(memberRepository.findByIdForUpdate(OWNER_ID)).thenReturn(Optional.of(previousOwner));
        }
        when(memberRepository.findByIdForUpdate(failingId)).thenThrow(failure);

        // when
        var exception = assertThrows(PessimisticLockingFailureException.class,
                () -> service.transfer(PROJECT_ID, OWNER_ID, TARGET_ID));

        // then
        assertSame(failure, exception);
        assertSame(previousOwner, project.getMember());
        verify(collaboratorRepository, never()).deleteByProjectIdAndMemberId(PROJECT_ID, TARGET_ID);
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @Test
    @DisplayName("본인에게 이전하는 요청은 회원을 잠그기 전에 거부한다")
    void transfer_SelfTarget_ThrowsTargetUnavailable() {
        // given
        Project project = project(member(OWNER_ID, Role.ROLE_USER, true));
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> service.transfer(PROJECT_ID, OWNER_ID, OWNER_ID));

        // then
        assertEquals(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE, exception.getCode());
        verifyNoInteractions(memberRepository, collaboratorRepository);
    }

    private Member member(Long id, Role role, boolean active) {
        Member member = Member.builder()
                .email("member-" + id + "@example.com")
                .password("encoded")
                .nickname("member")
                .role(role)
                .isActive(active)
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Project project(Member owner) {
        Project project = Project.builder()
                .title("project")
                .member(owner)
                .build();
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        return project;
    }
}
