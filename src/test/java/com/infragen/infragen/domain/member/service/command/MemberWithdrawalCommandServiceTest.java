package com.infragen.infragen.domain.member.service.command;

import com.infragen.infragen.domain.auth.service.TokenService;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorInvitationRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.service.command.ProjectCollaboratorInvitationCommandService;
import com.infragen.infragen.domain.project.service.command.ProjectOwnershipSuccessionCommandService;
import com.infragen.infragen.domain.project.service.command.ProjectOwnershipSuccessionPlan;
import com.infragen.infragen.domain.project.service.command.ProjectOwnershipSuccessionPlan.Deletion;
import com.infragen.infragen.domain.project.service.command.ProjectOwnershipSuccessionPlan.Transfer;
import com.infragen.infragen.global.apiPayload.code.GeneralErrorCode;
import com.infragen.infragen.global.apiPayload.exception.GeneralException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberWithdrawalCommandServiceTest {
    private static final Long MEMBER_ID = 5L;
    private static final Long SUCCESSOR_ID = 3L;

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectCollaboratorRepository collaboratorRepository;
    @Mock
    private ProjectCollaboratorInvitationRepository invitationRepository;
    @Mock
    private ProjectOwnershipSuccessionCommandService successionService;
    @Mock
    private ProjectCollaboratorInvitationCommandService invitationCommandService;
    @Mock
    private TokenService tokenService;
    @InjectMocks
    private MemberWithdrawalCommandService service;

    @Test
    @DisplayName("탈퇴는 프로젝트 잠금→승계 계획→회원 ID 순 잠금→승계 실행→초대 취소→참여 정리→비활성화→토큰 삭제 순서로 진행한다")
    void withdraw_OwnedParticipatingAndInvitedProjects_RunsStepsInOrder() {
        // given
        Member member = member(MEMBER_ID, Role.ROLE_USER, true);
        List<ProjectOwnershipSuccessionPlan> plans = List.of(
                new Transfer(30L, MEMBER_ID, SUCCESSOR_ID, Role.ROLE_USER),
                new Deletion(10L, MEMBER_ID));
        stubRelatedProjects(List.of(30L, 10L), List.of(20L), List.of(20L, 40L));
        when(successionService.prepareSuccessionPlans(MEMBER_ID, List.of(10L, 20L, 30L, 40L))).thenReturn(plans);
        when(memberRepository.findByIdForUpdate(SUCCESSOR_ID))
                .thenReturn(Optional.of(member(SUCCESSOR_ID, Role.ROLE_USER, true)));
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        // when
        service.withdraw(MEMBER_ID, Set.of(10L));

        // then
        InOrder order = inOrder(projectRepository, successionService, memberRepository,
                invitationCommandService, collaboratorRepository, tokenService);
        order.verify(projectRepository).findByIdForUpdate(10L);
        order.verify(projectRepository).findByIdForUpdate(20L);
        order.verify(projectRepository).findByIdForUpdate(30L);
        order.verify(projectRepository).findByIdForUpdate(40L);
        order.verify(successionService).prepareSuccessionPlans(MEMBER_ID, List.of(10L, 20L, 30L, 40L));
        order.verify(memberRepository).findByIdForUpdate(SUCCESSOR_ID);
        order.verify(memberRepository).findByIdForUpdate(MEMBER_ID);
        order.verify(successionService).executeSuccessionPlans(plans);
        order.verify(invitationCommandService).cancelRelatedPendingInvitations(MEMBER_ID, List.of(10L, 20L, 30L, 40L));
        order.verify(collaboratorRepository).deleteAllByMemberId(MEMBER_ID);
        order.verify(memberRepository).findByIdForUpdate(MEMBER_ID);
        order.verify(memberRepository).flush();
        order.verify(tokenService).deleteRefreshToken(MEMBER_ID);
        assertFalse(member.getIsActive());
    }

    @Test
    @DisplayName("승계 대상 회원이 떠나는 회원보다 ID가 커도 작은 ID부터 잠근다")
    void withdraw_SuccessorIdGreaterThanMember_LocksSmallerIdFirst() {
        // given
        Long largerSuccessorId = 9L;
        stubRelatedProjects(List.of(10L), List.of(), List.of());
        when(successionService.prepareSuccessionPlans(MEMBER_ID, List.of(10L)))
                .thenReturn(List.of(new Transfer(10L, MEMBER_ID, largerSuccessorId, Role.ROLE_USER)));
        when(memberRepository.findByIdForUpdate(largerSuccessorId))
                .thenReturn(Optional.of(member(largerSuccessorId, Role.ROLE_USER, true)));
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member(MEMBER_ID, Role.ROLE_USER, true)));

        // when
        service.withdraw(MEMBER_ID, Set.of());

        // then
        InOrder order = inOrder(memberRepository);
        order.verify(memberRepository).findByIdForUpdate(MEMBER_ID);
        order.verify(memberRepository).findByIdForUpdate(largerSuccessorId);
    }

    @Test
    @DisplayName("관련 프로젝트가 없어도 회원을 비활성화하고 토큰을 삭제한다")
    void withdraw_NoRelatedProjects_WithdrawsMemberOnly() {
        // given
        Member member = member(MEMBER_ID, Role.ROLE_USER, true);
        stubRelatedProjects(List.of(), List.of(), List.of());
        when(successionService.prepareSuccessionPlans(MEMBER_ID, List.of())).thenReturn(List.of());
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        // when
        service.withdraw(MEMBER_ID, Set.of());

        // then
        verify(projectRepository, never()).findByIdForUpdate(anyLong());
        verify(tokenService).deleteRefreshToken(MEMBER_ID);
        assertFalse(member.getIsActive());
    }

    @Test
    @DisplayName("삭제 계획만 있으면 승계 대상 회원은 잠그지 않는다")
    void withdraw_DeletionPlanOnly_LocksOnlyDepartingMember() {
        // given
        stubRelatedProjects(List.of(10L), List.of(), List.of());
        when(successionService.prepareSuccessionPlans(MEMBER_ID, List.of(10L)))
                .thenReturn(List.of(new Deletion(10L, MEMBER_ID)));
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member(MEMBER_ID, Role.ROLE_USER, true)));

        // when
        service.withdraw(MEMBER_ID, Set.of(10L));

        // then
        verify(memberRepository, never()).findByIdForUpdate(SUCCESSOR_ID);
    }

    @Test
    @DisplayName("guest는 프로젝트를 변경하기 전에 탈퇴를 거부한다")
    void withdraw_GuestMember_ThrowsGuestActionNotAllowed() {
        // given
        stubRelatedProjects(List.of(), List.of(), List.of());
        when(successionService.prepareSuccessionPlans(MEMBER_ID, List.of())).thenReturn(List.of());
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member(MEMBER_ID, Role.ROLE_GUEST, true)));

        // when
        MemberException exception = assertThrows(MemberException.class, () -> service.withdraw(MEMBER_ID, Set.of()));

        // then
        assertEquals(MemberErrorCode.GUEST_ACTION_NOT_ALLOWED, exception.getCode());
        verifyNoInteractions(invitationCommandService, tokenService);
        verify(successionService, never()).executeSuccessionPlans(anyList());
    }

    @Test
    @DisplayName("회원이 없거나 비활성이면 프로젝트를 변경하지 않고 탈퇴를 거부한다")
    void withdraw_MemberUnavailable_ThrowsMemberNotFound() {
        // given
        stubRelatedProjects(List.of(), List.of(), List.of());
        when(successionService.prepareSuccessionPlans(MEMBER_ID, List.of())).thenReturn(List.of());
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.empty());

        // when
        MemberException exception = assertThrows(MemberException.class, () -> service.withdraw(MEMBER_ID, Set.of()));

        // then
        assertEquals(MemberErrorCode.MEMBER_NOT_FOUND, exception.getCode());
        verify(successionService, never()).executeSuccessionPlans(anyList());
        verifyNoInteractions(invitationCommandService, tokenService);
    }

    @Test
    @DisplayName("회원 잠금 뒤 처음 잠그지 않은 프로젝트가 생겼으면 아무것도 변경하지 않고 중단한다")
    void withdraw_NewRelatedProjectAfterMemberLock_ThrowsConcurrentModification() {
        // given
        when(projectRepository.findOwnedProjectIdsOrderByIdAsc(MEMBER_ID)).thenReturn(List.of(10L), List.of(10L, 50L));
        when(collaboratorRepository.findParticipatingProjectIdsOrderByProjectIdAsc(MEMBER_ID)).thenReturn(List.of());
        when(invitationRepository.findPendingRelatedProjectIdsOrderByProjectIdAsc(MEMBER_ID)).thenReturn(List.of());
        when(successionService.prepareSuccessionPlans(MEMBER_ID, List.of(10L)))
                .thenReturn(List.of(new Deletion(10L, MEMBER_ID)));
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member(MEMBER_ID, Role.ROLE_USER, true)));

        // when
        GeneralException exception = assertThrows(GeneralException.class, () -> service.withdraw(MEMBER_ID, Set.of()));

        // then
        assertEquals(GeneralErrorCode.CONCURRENT_MODIFICATION, exception.getCode());
        verify(projectRepository, never()).findByIdForUpdate(50L);
        verify(successionService, never()).executeSuccessionPlans(anyList());
        verifyNoInteractions(invitationCommandService, tokenService);
        verify(collaboratorRepository, never()).deleteAllByMemberId(anyLong());
    }

    @Test
    @DisplayName("정리 뒤에도 프로젝트 관계가 남아 있으면 토큰을 삭제하지 않고 중단한다")
    void withdraw_RelationRemainsAfterCleanup_ThrowsConcurrentModification() {
        // given
        when(projectRepository.findOwnedProjectIdsOrderByIdAsc(MEMBER_ID)).thenReturn(List.of(10L));
        when(collaboratorRepository.findParticipatingProjectIdsOrderByProjectIdAsc(MEMBER_ID)).thenReturn(List.of());
        when(invitationRepository.findPendingRelatedProjectIdsOrderByProjectIdAsc(MEMBER_ID)).thenReturn(List.of());
        when(successionService.prepareSuccessionPlans(MEMBER_ID, List.of(10L)))
                .thenReturn(List.of(new Deletion(10L, MEMBER_ID)));
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member(MEMBER_ID, Role.ROLE_USER, true)));

        // when
        GeneralException exception = assertThrows(GeneralException.class, () -> service.withdraw(MEMBER_ID, Set.of(10L)));

        // then
        assertEquals(GeneralErrorCode.CONCURRENT_MODIFICATION, exception.getCode());
        verify(memberRepository).flush();
        verifyNoInteractions(tokenService);
    }

    @Test
    @DisplayName("승계 실행이 실패하면 예외를 전파하고 초대 취소·참여 정리·토큰 삭제를 하지 않는다")
    void withdraw_ExecutionFails_PropagatesAndStopsFollowingSteps() {
        // given
        List<ProjectOwnershipSuccessionPlan> plans = List.of(new Deletion(10L, MEMBER_ID));
        stubRelatedProjects(List.of(10L), List.of(), List.of());
        when(successionService.prepareSuccessionPlans(MEMBER_ID, List.of(10L))).thenReturn(plans);
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member(MEMBER_ID, Role.ROLE_USER, true)));
        IllegalStateException failure = new IllegalStateException("execute failed");
        doThrow(failure).when(successionService).executeSuccessionPlans(plans);

        // when
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.withdraw(MEMBER_ID, Set.of(10L)));

        // then
        assertSame(failure, exception);
        verifyNoInteractions(invitationCommandService, tokenService);
        verify(collaboratorRepository, never()).deleteAllByMemberId(anyLong());
    }

    @Test
    @DisplayName("프로젝트 잠금 실패를 전파하고 승계 계획을 만들지 않는다")
    void withdraw_ProjectLockFails_PropagatesBeforePlanning() {
        // given
        stubRelatedProjects(List.of(10L, 20L), List.of(), List.of());
        PessimisticLockingFailureException failure = new PessimisticLockingFailureException("lock failed");
        when(projectRepository.findByIdForUpdate(10L)).thenThrow(failure);

        // when
        PessimisticLockingFailureException exception =
                assertThrows(PessimisticLockingFailureException.class, () -> service.withdraw(MEMBER_ID, Set.of()));

        // then
        assertSame(failure, exception);
        verify(projectRepository, never()).findByIdForUpdate(20L);
        verifyNoInteractions(successionService, invitationCommandService, tokenService);
    }

    @Test
    @DisplayName("토큰 삭제가 실패하면 예외를 전파해 호출한 transaction이 DB 변경을 롤백하게 한다")
    void withdraw_TokenDeletionFails_Propagates() {
        // given
        stubRelatedProjects(List.of(), List.of(), List.of());
        when(successionService.prepareSuccessionPlans(MEMBER_ID, List.of())).thenReturn(List.of());
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member(MEMBER_ID, Role.ROLE_USER, true)));
        IllegalStateException failure = new IllegalStateException("redis failed");
        doThrow(failure).when(tokenService).deleteRefreshToken(MEMBER_ID);

        // when
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.withdraw(MEMBER_ID, Set.of()));

        // then
        assertSame(failure, exception);
        verify(memberRepository).flush();
    }

    @Test
    @DisplayName("확인하지 않은 삭제 프로젝트가 있으면 아무것도 변경하기 전에 탈퇴를 중단한다")
    void withdraw_UnconfirmedDeletion_ThrowsBeforeAnyChange() {
        // given
        stubRelatedProjects(List.of(10L, 20L), List.of(), List.of());
        when(successionService.prepareSuccessionPlans(MEMBER_ID, List.of(10L, 20L)))
                .thenReturn(List.of(new Deletion(10L, MEMBER_ID), new Deletion(20L, MEMBER_ID)));
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member(MEMBER_ID, Role.ROLE_USER, true)));

        // when
        MemberException exception = assertThrows(MemberException.class,
                () -> service.withdraw(MEMBER_ID, Set.of(10L)));

        // then
        assertEquals(MemberErrorCode.WITHDRAWAL_DELETION_NOT_CONFIRMED, exception.getCode());
        verify(successionService, never()).executeSuccessionPlans(anyList());
        verify(collaboratorRepository, never()).deleteAllByMemberId(anyLong());
        verifyNoInteractions(invitationCommandService, tokenService);
    }

    @Test
    @DisplayName("삭제 계획이 있는데 확인 목록이 비어 있어도 탈퇴를 중단한다")
    void withdraw_DeletionWithEmptyConfirmation_ThrowsNotConfirmed() {
        // given
        stubRelatedProjects(List.of(10L), List.of(), List.of());
        when(successionService.prepareSuccessionPlans(MEMBER_ID, List.of(10L)))
                .thenReturn(List.of(new Deletion(10L, MEMBER_ID)));
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member(MEMBER_ID, Role.ROLE_USER, true)));

        // when
        MemberException exception = assertThrows(MemberException.class,
                () -> service.withdraw(MEMBER_ID, Set.of()));

        // then
        assertEquals(MemberErrorCode.WITHDRAWAL_DELETION_NOT_CONFIRMED, exception.getCode());
        verify(successionService, never()).executeSuccessionPlans(anyList());
    }

    @Test
    @DisplayName("확인한 목록이 실제 삭제보다 넓거나 승계 프로젝트가 섞여 있어도 탈퇴를 진행한다")
    void withdraw_ConfirmationCoversDeletions_Proceeds() {
        // given
        List<ProjectOwnershipSuccessionPlan> plans = List.of(
                new Transfer(30L, MEMBER_ID, SUCCESSOR_ID, Role.ROLE_USER),
                new Deletion(10L, MEMBER_ID));
        stubRelatedProjects(List.of(10L, 30L), List.of(), List.of());
        when(successionService.prepareSuccessionPlans(MEMBER_ID, List.of(10L, 30L))).thenReturn(plans);
        when(memberRepository.findByIdForUpdate(SUCCESSOR_ID))
                .thenReturn(Optional.of(member(SUCCESSOR_ID, Role.ROLE_USER, true)));
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member(MEMBER_ID, Role.ROLE_USER, true)));

        // when
        service.withdraw(MEMBER_ID, Set.of(10L, 30L, 99L));

        // then
        verify(successionService).executeSuccessionPlans(plans);
        verify(tokenService).deleteRefreshToken(MEMBER_ID);
    }

    @Test
    @DisplayName("새 프로젝트 관계가 생겼으면 확인 여부와 관계없이 동시 수정 오류가 먼저 발생한다")
    void withdraw_NewRelatedProjectAndUnconfirmedDeletion_ThrowsConcurrentModificationFirst() {
        // given
        stubRelatedProjects(List.of(10L), List.of(), List.of());
        when(projectRepository.findOwnedProjectIdsOrderByIdAsc(MEMBER_ID))
                .thenReturn(List.of(10L), List.of(10L, 50L));
        when(successionService.prepareSuccessionPlans(MEMBER_ID, List.of(10L)))
                .thenReturn(List.of(new Deletion(10L, MEMBER_ID)));
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member(MEMBER_ID, Role.ROLE_USER, true)));

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.withdraw(MEMBER_ID, Set.of()));

        // then
        assertEquals(GeneralErrorCode.CONCURRENT_MODIFICATION, exception.getCode());
    }

    // 관련 project ID 조회는 처음·회원 잠금 뒤 재확인·정리 뒤 확인까지 최대 세 번 호출된다.
    // 처음과 재확인에는 같은 목록을, 정리 뒤 확인에는 빈 목록을 반환한다.
    private void stubRelatedProjects(List<Long> owned, List<Long> participating, List<Long> pending) {
        when(projectRepository.findOwnedProjectIdsOrderByIdAsc(MEMBER_ID))
                .thenReturn(owned, owned, List.of());
        when(collaboratorRepository.findParticipatingProjectIdsOrderByProjectIdAsc(MEMBER_ID))
                .thenReturn(participating, participating, List.of());
        when(invitationRepository.findPendingRelatedProjectIdsOrderByProjectIdAsc(MEMBER_ID))
                .thenReturn(pending, pending, List.of());
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
}
