package com.infragen.infragen.domain.project.service.command;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.entity.ProjectCollaboratorInvitation;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorInvitationStatus;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorInvitationRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCollaboratorInvitationCommandServiceTest {
    private static final Long PROJECT_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long INVITEE_ID = 20L;
    private static final String INVITEE_CODE = "A1B2C3D4";

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private MemberQueryService memberQueryService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ProjectCollaboratorRepository collaboratorRepository;

    @Mock
    private ProjectCollaboratorInvitationRepository invitationRepository;

    @InjectMocks
    private ProjectCollaboratorInvitationCommandService service;

    @Test
    @DisplayName("guest owner가 활성 guest를 초대하면 24시간 유효한 대기 초대를 저장한다")
    void invite_EligibleGuest_SavesPendingInvitation() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_GUEST, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_GUEST, "invitee@test.com");
        Project project = project(PROJECT_ID, owner);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(memberQueryService.findByInvitationCode(INVITEE_CODE)).thenReturn(invitee);
        stubInviteMemberLocks(owner, invitee);
        when(collaboratorRepository.existsByProjectIdAndMemberId(PROJECT_ID, INVITEE_ID))
                .thenReturn(false);
        when(invitationRepository.existsByProjectIdAndInviteeIdAndStatusAndExpiresAtAfter(
                eq(PROJECT_ID),
                eq(INVITEE_ID),
                eq(ProjectCollaboratorInvitationStatus.PENDING),
                any(LocalDateTime.class)
        )).thenReturn(false);
        LocalDateTime before = LocalDateTime.now();

        // when
        service.invite(PROJECT_ID, OWNER_ID, INVITEE_CODE, ProjectCollaboratorRole.EDITOR);

        // then
        ArgumentCaptor<ProjectCollaboratorInvitation> invitationCaptor =
                ArgumentCaptor.forClass(ProjectCollaboratorInvitation.class);
        verify(invitationRepository).save(invitationCaptor.capture());
        ProjectCollaboratorInvitation invitation = invitationCaptor.getValue();
        assertSame(project, invitation.getProject());
        assertSame(owner, invitation.getInvitedBy());
        assertSame(invitee, invitation.getInvitee());
        assertEquals(ProjectCollaboratorRole.EDITOR, invitation.getRole());
        assertEquals(ProjectCollaboratorInvitationStatus.PENDING, invitation.getStatus());
        assertFalse(invitation.getExpiresAt().isBefore(before.plusHours(24)));
        assertFalse(invitation.getExpiresAt().isAfter(LocalDateTime.now().plusHours(24)));
        verify(projectRepository).findByIdForUpdate(PROJECT_ID);
    }

    @Test
    @DisplayName("초대코드가 없거나 활성 회원이 아니면 동일한 프로젝트 오류를 반환한다")
    void invite_UnknownInvitationCode_ThrowsTargetUnavailable() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_GUEST, "owner@test.com");
        Project project = project(PROJECT_ID, owner);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(memberQueryService.findByInvitationCode(INVITEE_CODE))
                .thenThrow(new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> service.invite(PROJECT_ID, OWNER_ID, INVITEE_CODE, ProjectCollaboratorRole.VIEWER)
        );

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_TARGET_UNAVAILABLE, exception.getCode());
        verify(invitationRepository, never()).save(any(ProjectCollaboratorInvitation.class));
    }

    @Test
    @DisplayName("guest owner는 일반 회원을 초대할 수 없다")
    void invite_GuestOwnerTargetsRegularMember_ThrowsTargetUnavailable() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_GUEST, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        Project project = project(PROJECT_ID, owner);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(memberQueryService.findByInvitationCode(INVITEE_CODE)).thenReturn(invitee);
        stubInviteMemberLocks(owner, invitee);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> service.invite(PROJECT_ID, OWNER_ID, INVITEE_CODE, ProjectCollaboratorRole.VIEWER)
        );

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_TARGET_UNAVAILABLE, exception.getCode());
        verify(collaboratorRepository, never()).existsByProjectIdAndMemberId(PROJECT_ID, INVITEE_ID);
        verify(invitationRepository, never()).save(any(ProjectCollaboratorInvitation.class));
    }

    @Test
    @DisplayName("일반 owner는 guest를 초대할 수 없다")
    void invite_RegularOwnerTargetsGuest_ThrowsTargetUnavailable() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_GUEST, "invitee@test.com");
        Project project = project(PROJECT_ID, owner);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(memberQueryService.findByInvitationCode(INVITEE_CODE)).thenReturn(invitee);
        stubInviteMemberLocks(owner, invitee);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> service.invite(PROJECT_ID, OWNER_ID, INVITEE_CODE, ProjectCollaboratorRole.VIEWER)
        );

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_TARGET_UNAVAILABLE, exception.getCode());
        verify(collaboratorRepository, never()).existsByProjectIdAndMemberId(PROJECT_ID, INVITEE_ID);
        verify(invitationRepository, never()).save(any(ProjectCollaboratorInvitation.class));
    }

    @Test
    @DisplayName("같은 대상에게 대기 중인 초대가 있으면 다시 초대하지 않는다")
    void invite_PendingInvitationExists_ThrowsConflict() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_GUEST, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_GUEST, "invitee@test.com");
        Project project = project(PROJECT_ID, owner);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(memberQueryService.findByInvitationCode(INVITEE_CODE)).thenReturn(invitee);
        stubInviteMemberLocks(owner, invitee);
        when(collaboratorRepository.existsByProjectIdAndMemberId(PROJECT_ID, INVITEE_ID))
                .thenReturn(false);
        when(invitationRepository.existsByProjectIdAndInviteeIdAndStatusAndExpiresAtAfter(
                eq(PROJECT_ID),
                eq(INVITEE_ID),
                eq(ProjectCollaboratorInvitationStatus.PENDING),
                any(LocalDateTime.class)
        )).thenReturn(true);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> service.invite(PROJECT_ID, OWNER_ID, INVITEE_CODE, ProjectCollaboratorRole.VIEWER)
        );

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_ALREADY_PENDING, exception.getCode());
        verify(invitationRepository, never()).save(any(ProjectCollaboratorInvitation.class));
    }

    @Test
    @DisplayName("다른 회원 소유 프로젝트에는 초대를 보낼 수 없다")
    void invite_NotProjectOwner_ThrowsProjectNotFound() {
        // given
        Member anotherOwner = member(99L, Role.ROLE_GUEST, "another-owner@test.com");
        Project project = project(PROJECT_ID, anotherOwner);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> service.invite(PROJECT_ID, OWNER_ID, INVITEE_CODE, ProjectCollaboratorRole.VIEWER)
        );

        // then
        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verify(memberQueryService, never()).findByInvitationCode(any(String.class));
        verify(invitationRepository, never()).save(any(ProjectCollaboratorInvitation.class));
    }

    @Test
    @DisplayName("이미 collaborator인 회원은 초대하지 않는다")
    void invite_AlreadyCollaborator_ThrowsConflict() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_GUEST, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_GUEST, "invitee@test.com");
        Project project = project(PROJECT_ID, owner);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(memberQueryService.findByInvitationCode(INVITEE_CODE)).thenReturn(invitee);
        stubInviteMemberLocks(owner, invitee);
        when(collaboratorRepository.existsByProjectIdAndMemberId(PROJECT_ID, INVITEE_ID))
                .thenReturn(true);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> service.invite(PROJECT_ID, OWNER_ID, INVITEE_CODE, ProjectCollaboratorRole.VIEWER)
        );

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_ALREADY_EXISTS, exception.getCode());
        verify(invitationRepository, never()).save(any(ProjectCollaboratorInvitation.class));
    }

    @Test
    @DisplayName("대상 회원이 수락하면 초대와 collaborator를 함께 처리한다")
    void accept_PendingInvitation_CreatesCollaborator() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_GUEST, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_GUEST, "invitee@test.com");
        Project project = project(PROJECT_ID, owner);
        ProjectCollaboratorInvitation invitation = invitation(project, owner, invitee);
        stubResponseLocks(invitation);
        when(collaboratorRepository.existsByProjectIdAndMemberId(PROJECT_ID, INVITEE_ID))
                .thenReturn(false);

        // when
        service.accept(100L, INVITEE_ID);

        // then
        verifyResponseLockOrder();
        assertEquals(ProjectCollaboratorInvitationStatus.ACCEPTED, invitation.getStatus());
        assertSame(invitee, invitation.getRespondedBy());
        assertNotNull(invitation.getRespondedAt());
        ArgumentCaptor<ProjectCollaborator> collaboratorCaptor =
                ArgumentCaptor.forClass(ProjectCollaborator.class);
        verify(collaboratorRepository).save(collaboratorCaptor.capture());
        assertSame(project, collaboratorCaptor.getValue().getProject());
        assertSame(invitee, collaboratorCaptor.getValue().getMember());
        assertEquals(ProjectCollaboratorRole.EDITOR, collaboratorCaptor.getValue().getRole());
    }

    @Test
    @DisplayName("계정 유형이 다른 기존 대기 초대는 수락해도 참여자를 만들지 않는다")
    void accept_CrossTypePendingInvitation_ThrowsUnavailable() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_GUEST, "invitee@test.com");
        ProjectCollaboratorInvitation invitation = invitation(project(PROJECT_ID, owner), owner, invitee);
        stubResponseLocks(invitation);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> service.accept(100L, INVITEE_ID)
        );

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode());
        assertEquals(ProjectCollaboratorInvitationStatus.PENDING, invitation.getStatus());
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @Test
    @DisplayName("일반 회원끼리의 대기 초대는 수락해 참여자를 만든다")
    void accept_RegularMembers_CreatesCollaborator() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        ProjectCollaboratorInvitation invitation = invitation(project(PROJECT_ID, owner), owner, invitee);
        stubResponseLocks(invitation);

        // when
        service.accept(100L, INVITEE_ID);

        // then
        assertEquals(ProjectCollaboratorInvitationStatus.ACCEPTED, invitation.getStatus());
        verify(collaboratorRepository).save(any(ProjectCollaborator.class));
    }

    @Test
    @DisplayName("대상 회원이 거절하면 초대만 DECLINED로 변경한다")
    void decline_PendingInvitation_ChangesStatusWithoutCollaborator() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_GUEST, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_GUEST, "invitee@test.com");
        Project project = project(PROJECT_ID, owner);
        ProjectCollaboratorInvitation invitation = invitation(project, owner, invitee);
        stubResponseLocks(invitation);

        // when
        service.decline(100L, INVITEE_ID);

        // then
        verifyResponseLockOrder();
        assertEquals(ProjectCollaboratorInvitationStatus.DECLINED, invitation.getStatus());
        assertSame(invitee, invitation.getRespondedBy());
        assertNotNull(invitation.getRespondedAt());
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @Test
    @DisplayName("만료된 초대는 수락할 수 없다")
    void accept_ExpiredInvitation_ThrowsUnavailable() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_GUEST, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_GUEST, "invitee@test.com");
        Project project = project(PROJECT_ID, owner);
        ProjectCollaboratorInvitation invitation = ProjectCollaboratorInvitation.builder()
                .project(project)
                .invitedBy(owner)
                .invitee(invitee)
                .role(ProjectCollaboratorRole.EDITOR)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        stubResponseLocks(invitation);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> service.accept(100L, INVITEE_ID)
        );

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode());
        assertEquals(ProjectCollaboratorInvitationStatus.PENDING, invitation.getStatus());
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @Test
    @DisplayName("이미 collaborator인 회원은 초대를 수락할 수 없다")
    void accept_AlreadyCollaborator_ThrowsConflict() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_GUEST, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_GUEST, "invitee@test.com");
        Project project = project(PROJECT_ID, owner);
        ProjectCollaboratorInvitation invitation = invitation(project, owner, invitee);
        stubResponseLocks(invitation);
        when(collaboratorRepository.existsByProjectIdAndMemberId(PROJECT_ID, INVITEE_ID))
                .thenReturn(true);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> service.accept(100L, INVITEE_ID)
        );

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_ALREADY_EXISTS, exception.getCode());
        assertEquals(ProjectCollaboratorInvitationStatus.PENDING, invitation.getStatus());
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("본인 초대를 찾지 못하면 프로젝트나 초대를 잠그지 않는다")
    void respond_InvitationNotOwnedOrMissing_ThrowsUnavailable(boolean accept) {
        // given
        when(invitationRepository.findProjectIdByIdAndInviteeId(100L, INVITEE_ID))
                .thenReturn(Optional.empty());

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> respond(accept));

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode());
        verify(projectRepository, never()).findByIdForUpdate(any(Long.class));
        verifyNoInteractions(memberRepository);
        verify(invitationRepository, never()).findByIdAndInviteeIdForUpdate(100L, INVITEE_ID);
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("최초 조회 뒤 프로젝트가 삭제되면 초대 응답을 거부한다")
    void respond_ProjectDeletedBeforeLock_ThrowsUnavailable(boolean accept) {
        // given
        when(invitationRepository.findProjectIdByIdAndInviteeId(100L, INVITEE_ID))
                .thenReturn(Optional.of(PROJECT_ID));
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.empty());

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> respond(accept));

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode());
        verifyNoInteractions(memberRepository);
        verify(invitationRepository, never()).findByIdAndInviteeIdForUpdate(100L, INVITEE_ID);
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("프로젝트 잠금 뒤 초대가 없거나 활성 응답 대상이 아니면 응답을 거부한다")
    void respond_LockedInvitationUnavailable_ThrowsUnavailable(boolean accept) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        when(invitationRepository.findProjectIdByIdAndInviteeId(100L, INVITEE_ID))
                .thenReturn(Optional.of(PROJECT_ID));
        when(projectRepository.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(project(PROJECT_ID, owner)));
        when(memberRepository.findByIdForUpdate(INVITEE_ID))
                .thenReturn(Optional.of(member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com")));
        when(invitationRepository.findByIdAndInviteeIdForUpdate(100L, INVITEE_ID))
                .thenReturn(Optional.empty());

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> respond(accept));

        // then
        verifyResponseLockOrder();
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode());
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @ParameterizedTest
    @CsvSource({"true, ACCEPTED", "true, DECLINED", "true, CANCELLED",
            "false, ACCEPTED", "false, DECLINED", "false, CANCELLED"})
    @DisplayName("잠금 조회에서 이미 처리된 초대는 다시 수락하거나 거절하지 않는다")
    void respond_AlreadyProcessed_ThrowsUnavailable(
            boolean accept,
            ProjectCollaboratorInvitationStatus status
    ) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        ProjectCollaboratorInvitation invitation = invitation(project(PROJECT_ID, owner), owner, invitee);
        if (status == ProjectCollaboratorInvitationStatus.ACCEPTED) {
            invitation.accept(invitee, LocalDateTime.now().minusMinutes(1));
        } else if (status == ProjectCollaboratorInvitationStatus.DECLINED) {
            invitation.decline(invitee, LocalDateTime.now().minusMinutes(1));
        } else {
            invitation.cancel(owner, LocalDateTime.now().minusMinutes(1));
        }
        Member respondedBy = invitation.getRespondedBy();
        LocalDateTime respondedAt = invitation.getRespondedAt();
        stubResponseLocks(invitation);

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> respond(accept));

        // then
        verifyResponseLockOrder();
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode());
        assertEquals(status, invitation.getStatus());
        assertSame(respondedBy, invitation.getRespondedBy());
        assertEquals(respondedAt, invitation.getRespondedAt());
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("잠금을 기다리는 동안 만료된 초대는 응답을 거부한다")
    void respond_ExpiresWhileWaitingForProjectLock_ThrowsUnavailable(boolean accept) {
        // given
        LocalDateTime beforeWait = LocalDateTime.of(2026, 9, 28, 12, 0);
        LocalDateTime expiresAt = beforeWait.plusSeconds(1);
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        Project project = project(PROJECT_ID, owner);
        ProjectCollaboratorInvitation invitation = ProjectCollaboratorInvitation.builder()
                .project(project)
                .invitedBy(owner)
                .invitee(invitee)
                .role(ProjectCollaboratorRole.EDITOR)
                .expiresAt(expiresAt)
                .build();
        stubResponseLocks(invitation);

        try (MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            time.when(LocalDateTime::now).thenReturn(beforeWait);
            when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenAnswer(invocation -> {
                time.when(LocalDateTime::now).thenReturn(expiresAt);
                return Optional.of(project);
            });

            // when
            ProjectException exception = assertThrows(ProjectException.class, () -> respond(accept));

            // then
            verifyResponseLockOrder();
            assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode());
            assertEquals(ProjectCollaboratorInvitationStatus.PENDING, invitation.getStatus());
            verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("잠금 조회가 반환한 대상도 비활성 상태이면 응답을 거부한다")
    void respond_InactiveInvitee_ThrowsUnavailable(boolean accept) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        invitee.withdraw();
        ProjectCollaboratorInvitation invitation = invitation(project(PROJECT_ID, owner), owner, invitee);
        stubResponseLocks(invitation);
        when(memberRepository.findByIdForUpdate(INVITEE_ID))
                .thenReturn(Optional.of(member(INVITEE_ID, Role.ROLE_USER, "locked@test.com")));

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> respond(accept));

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode());
        assertEquals(ProjectCollaboratorInvitationStatus.PENDING, invitation.getStatus());
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @Test
    @DisplayName("잠금 시점에 초대 대상이 이미 owner이면 참여자로 등록하지 않는다")
    void accept_InviteeBecameOwner_ThrowsUnavailable() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        Project project = project(PROJECT_ID, owner);
        ProjectCollaboratorInvitation invitation = invitation(project, owner, invitee);
        stubResponseLocks(invitation);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenAnswer(invocation -> {
            project.transferOwnershipTo(invitee);
            return Optional.of(project);
        });

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> service.accept(100L, INVITEE_ID));

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode());
        assertEquals(ProjectCollaboratorInvitationStatus.PENDING, invitation.getStatus());
        verify(collaboratorRepository, never()).existsByProjectIdAndMemberId(PROJECT_ID, INVITEE_ID);
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @Test
    @DisplayName("발신자가 아니라 잠금 이후의 현재 owner 계정 유형으로 수락을 판단한다")
    void accept_CurrentOwnerTypeChanged_ThrowsUnavailable() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        Member newOwner = member(30L, Role.ROLE_GUEST, "new-owner@test.com");
        Project project = project(PROJECT_ID, owner);
        ProjectCollaboratorInvitation invitation = invitation(project, owner, invitee);
        stubResponseLocks(invitation);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenAnswer(invocation -> {
            project.transferOwnershipTo(newOwner);
            return Optional.of(project);
        });

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> service.accept(100L, INVITEE_ID));

        // then
        assertSame(owner, invitation.getInvitedBy());
        assertSame(newOwner, project.getMember());
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode());
        assertEquals(ProjectCollaboratorInvitationStatus.PENDING, invitation.getStatus());
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @Test
    @DisplayName("현재 owner와 계정 유형이 다른 초대도 대상이 거절할 수 있다")
    void decline_CrossTypeInvitation_ChangesStatus() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_GUEST, "invitee@test.com");
        ProjectCollaboratorInvitation invitation = invitation(project(PROJECT_ID, owner), owner, invitee);
        stubResponseLocks(invitation);

        // when
        service.decline(100L, INVITEE_ID);

        // then
        verifyResponseLockOrder();
        assertEquals(ProjectCollaboratorInvitationStatus.DECLINED, invitation.getStatus());
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("응답 회원의 잠금 조회가 없으면 초대를 잠그거나 변경하지 않는다")
    void respond_LockedMemberMissing_ThrowsUnavailable(boolean accept) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        when(invitationRepository.findProjectIdByIdAndInviteeId(100L, INVITEE_ID))
                .thenReturn(Optional.of(PROJECT_ID));
        when(projectRepository.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(project(PROJECT_ID, owner)));
        when(memberRepository.findByIdForUpdate(INVITEE_ID)).thenReturn(Optional.empty());

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> respond(accept));

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode());
        verify(memberRepository).findByIdForUpdate(INVITEE_ID);
        verify(invitationRepository, never()).findByIdAndInviteeIdForUpdate(100L, INVITEE_ID);
        verifyNoInteractions(collaboratorRepository);
    }

    @ParameterizedTest
    @CsvSource(value = {"true,false", "false,false", "true,NULL", "false,NULL"}, nullValues = "NULL")
    @DisplayName("잠금 조회한 회원이 활성이 아니면 대기 초대와 참여 기록을 변경하지 않는다")
    void respond_InactiveLockedMember_ThrowsUnavailable(boolean accept, Boolean active) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com", active);
        ProjectCollaboratorInvitation invitation = invitation(project(PROJECT_ID, owner), owner, invitee);
        when(invitationRepository.findProjectIdByIdAndInviteeId(100L, INVITEE_ID))
                .thenReturn(Optional.of(PROJECT_ID));
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(invitation.getProject()));
        when(memberRepository.findByIdForUpdate(INVITEE_ID)).thenReturn(Optional.of(invitee));

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> respond(accept));

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode());
        assertEquals(ProjectCollaboratorInvitationStatus.PENDING, invitation.getStatus());
        assertNull(invitation.getRespondedBy());
        assertNull(invitation.getRespondedAt());
        verify(invitationRepository, never()).findByIdAndInviteeIdForUpdate(100L, INVITEE_ID);
        verifyNoInteractions(collaboratorRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("응답 회원 잠금 실패는 초대를 잠그기 전에 전파한다")
    void respond_MemberLockFailure_PropagatesWithoutMutation(boolean accept) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        var failure = new PessimisticLockingFailureException("member lock failed");
        when(invitationRepository.findProjectIdByIdAndInviteeId(100L, INVITEE_ID))
                .thenReturn(Optional.of(PROJECT_ID));
        when(projectRepository.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(project(PROJECT_ID, owner)));
        when(memberRepository.findByIdForUpdate(INVITEE_ID)).thenThrow(failure);

        // when
        var exception = assertThrows(PessimisticLockingFailureException.class, () -> respond(accept));

        // then
        assertSame(failure, exception);
        verify(invitationRepository, never()).findByIdAndInviteeIdForUpdate(100L, INVITEE_ID);
        verifyNoInteractions(collaboratorRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("회원 잠금을 기다리는 동안 만료 시각에 도달하면 초대 응답을 거부한다")
    void respond_ExpiresDuringMemberLock_ThrowsUnavailable(boolean accept) {
        // given
        LocalDateTime beforeWait = LocalDateTime.of(2026, 9, 29, 12, 0);
        LocalDateTime expiresAt = beforeWait.plusSeconds(1);
        AtomicReference<LocalDateTime> currentTime = new AtomicReference<>(beforeWait);
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        ProjectCollaboratorInvitation invitation = ProjectCollaboratorInvitation.builder()
                .project(project(PROJECT_ID, owner))
                .invitedBy(owner)
                .invitee(invitee)
                .role(ProjectCollaboratorRole.EDITOR)
                .expiresAt(expiresAt)
                .build();
        stubResponseLocks(invitation);
        when(memberRepository.findByIdForUpdate(INVITEE_ID)).thenAnswer(invocation -> {
            currentTime.set(expiresAt);
            return Optional.of(invitee);
        });
        try (MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            time.when(LocalDateTime::now).thenAnswer(invocation -> currentTime.get());

            // when
            ProjectException exception = assertThrows(ProjectException.class, () -> respond(accept));

            // then
            verifyResponseLockOrder();
            assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode());
            assertEquals(ProjectCollaboratorInvitationStatus.PENDING, invitation.getStatus());
            assertNull(invitation.getRespondedBy());
            assertNull(invitation.getRespondedAt());
            verifyNoInteractions(collaboratorRepository);
        }
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    @DisplayName("잠금 조회한 초대의 프로젝트나 대상이 다르면 응답을 거부한다")
    void respond_LockedInvitationScopeMismatch_ThrowsUnavailable(boolean accept, boolean differentProject) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        ProjectCollaboratorInvitation invitation = invitation(
                project(differentProject ? 2L : PROJECT_ID, owner), owner,
                differentProject ? invitee : member(30L, Role.ROLE_USER, "other@test.com"));
        when(invitationRepository.findProjectIdByIdAndInviteeId(100L, INVITEE_ID))
                .thenReturn(Optional.of(PROJECT_ID));
        when(projectRepository.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(project(PROJECT_ID, owner)));
        when(memberRepository.findByIdForUpdate(INVITEE_ID)).thenReturn(Optional.of(invitee));
        when(invitationRepository.findByIdAndInviteeIdForUpdate(100L, INVITEE_ID))
                .thenReturn(Optional.of(invitation));

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> respond(accept));

        // then
        verifyResponseLockOrder();
        assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode());
        assertEquals(ProjectCollaboratorInvitationStatus.PENDING, invitation.getStatus());
        assertNull(invitation.getRespondedBy());
        assertNull(invitation.getRespondedAt());
        verifyNoInteractions(collaboratorRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("떠나는 회원이 발신자 또는 대상인 대기 초대만 취소한다")
    void cancelRelatedPendingInvitations_RelatedPending_RecordsCancellation(boolean sender) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        Member departingMember = sender ? owner : invitee;
        Project project = project(PROJECT_ID, owner);
        ProjectCollaboratorInvitation invitation = invitation(project, owner, invitee);
        stubCancellation(departingMember, project, List.of(invitation));

        // when
        service.cancelRelatedPendingInvitations(departingMember.getId(), List.of(PROJECT_ID));

        // then
        assertEquals(ProjectCollaboratorInvitationStatus.CANCELLED, invitation.getStatus());
        assertSame(departingMember, invitation.getRespondedBy());
        assertNotNull(invitation.getRespondedAt());
        InOrder order = inOrder(memberRepository, invitationRepository);
        order.verify(memberRepository).findByIdForUpdate(departingMember.getId());
        order.verify(invitationRepository)
                .findPendingRelatedProjectIdsOrderByProjectIdAsc(departingMember.getId());
        order.verify(invitationRepository)
                .findPendingRelatedByProjectIdAndMemberIdForUpdate(PROJECT_ID, departingMember.getId());
        verify(invitationRepository, never()).deleteAllByMemberId(departingMember.getId());
        verify(invitationRepository, never()).deleteByProjectId(PROJECT_ID);
        verifyNoInteractions(projectRepository, collaboratorRepository);
    }

    @Test
    @DisplayName("만료 시각이 지난 대기 초대도 취소 기록을 남긴다")
    void cancelRelatedPendingInvitations_ExpiredPending_CancelsWithoutChangingExpiry() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Project project = project(PROJECT_ID, owner);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 9, 1, 12, 0);
        ProjectCollaboratorInvitation invitation = ProjectCollaboratorInvitation.builder()
                .project(project).invitedBy(owner)
                .invitee(member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com"))
                .role(ProjectCollaboratorRole.EDITOR).expiresAt(expiresAt).build();
        stubCancellation(owner, project, List.of(invitation));

        // when
        service.cancelRelatedPendingInvitations(OWNER_ID, List.of(PROJECT_ID));

        // then
        assertEquals(ProjectCollaboratorInvitationStatus.CANCELLED, invitation.getStatus());
        assertEquals(expiresAt, invitation.getExpiresAt());
        assertSame(owner, invitation.getRespondedBy());
    }

    @ParameterizedTest
    @EnumSource(value = ProjectCollaboratorInvitationStatus.class, names = {"ACCEPTED", "DECLINED", "CANCELLED"})
    @DisplayName("잠금 조회에서 이미 처리된 초대는 취소가 기존 상태와 처리 기록을 변경하지 않는다")
    void cancelRelatedPendingInvitations_ProcessedAtLock_PreservesHistory(
            ProjectCollaboratorInvitationStatus status
    ) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        Project project = project(PROJECT_ID, owner);
        ProjectCollaboratorInvitation invitation = invitation(project, owner, invitee);
        LocalDateTime processedAt = LocalDateTime.of(2026, 9, 1, 12, 0);
        switch (status) {
            case ACCEPTED -> invitation.accept(invitee, processedAt);
            case DECLINED -> invitation.decline(invitee, processedAt);
            case CANCELLED -> invitation.cancel(invitee, processedAt);
            default -> throw new IllegalStateException("처리된 상태만 사용한다.");
        }
        Member processor = invitation.getRespondedBy();
        stubCancellation(owner, project, List.of(invitation));

        // when
        service.cancelRelatedPendingInvitations(OWNER_ID, List.of(PROJECT_ID));

        // then
        assertEquals(status, invitation.getStatus());
        assertSame(processor, invitation.getRespondedBy());
        assertEquals(processedAt, invitation.getRespondedAt());
        verify(invitationRepository, never()).deleteAllByMemberId(OWNER_ID);
    }

    @Test
    @DisplayName("잠긴 project라도 관련 대기 초대가 없으면 초대 잠금 조회를 하지 않는다")
    void cancelRelatedPendingInvitations_LockedProjectWithoutPending_SkipsInvitationLock() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        when(memberRepository.findByIdForUpdate(OWNER_ID)).thenReturn(Optional.of(owner));
        when(invitationRepository.findPendingRelatedProjectIdsOrderByProjectIdAsc(OWNER_ID))
                .thenReturn(List.of());

        // when
        service.cancelRelatedPendingInvitations(OWNER_ID, List.of(1L, 2L));

        // then
        verify(invitationRepository, never())
                .findPendingRelatedByProjectIdAndMemberIdForUpdate(any(Long.class), any(Long.class));
        verifyNoInteractions(projectRepository, collaboratorRepository);
    }

    @Test
    @DisplayName("잠긴 범위 밖 project의 관련 대기 초대는 잠그거나 취소하지 않는다")
    void cancelRelatedPendingInvitations_PendingOutsideLockedRange_DoesNotLockOrCancel() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Project lockedProject = project(1L, owner);
        ProjectCollaboratorInvitation invitation = invitation(lockedProject, owner,
                member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com"));
        when(memberRepository.findByIdForUpdate(OWNER_ID)).thenReturn(Optional.of(owner));
        when(invitationRepository.findPendingRelatedProjectIdsOrderByProjectIdAsc(OWNER_ID))
                .thenReturn(List.of(1L, 5L));
        when(invitationRepository.findPendingRelatedByProjectIdAndMemberIdForUpdate(1L, OWNER_ID))
                .thenReturn(List.of(invitation));

        // when
        service.cancelRelatedPendingInvitations(OWNER_ID, List.of(1L));

        // then
        assertEquals(ProjectCollaboratorInvitationStatus.CANCELLED, invitation.getStatus());
        verify(invitationRepository, never()).findPendingRelatedByProjectIdAndMemberIdForUpdate(5L, OWNER_ID);
        verifyNoInteractions(projectRepository);
    }

    @Test
    @DisplayName("잠금 대기 중 초대가 삭제되거나 수락되어 대기가 없으면 취소하지 않는다")
    void cancelRelatedPendingInvitations_NoPendingAfterLock_DoesNothing() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Project project = project(PROJECT_ID, owner);
        stubCancellation(owner, project, List.of());

        // when
        service.cancelRelatedPendingInvitations(OWNER_ID, List.of(PROJECT_ID));

        // then
        verify(invitationRepository).findPendingRelatedByProjectIdAndMemberIdForUpdate(PROJECT_ID, OWNER_ID);
        verify(invitationRepository, never()).save(any(ProjectCollaboratorInvitation.class));
        verify(invitationRepository, never()).deleteAllByMemberId(OWNER_ID);
        verifyNoInteractions(collaboratorRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("잠금 이후 프로젝트나 회원 관련성이 다르면 대기 초대를 취소하지 않는다")
    void cancelRelatedPendingInvitations_ScopeMismatch_SkipsInvitation(boolean differentProject) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member anotherMember = member(30L, Role.ROLE_USER, "another@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        Project lockedProject = project(PROJECT_ID, owner);
        Project invitationProject = differentProject ? project(2L, owner) : lockedProject;
        ProjectCollaboratorInvitation invitation = invitation(invitationProject,
                differentProject ? owner : anotherMember, invitee);
        stubCancellation(owner, lockedProject, List.of(invitation));

        // when
        service.cancelRelatedPendingInvitations(OWNER_ID, List.of(PROJECT_ID));

        // then
        assertEquals(ProjectCollaboratorInvitationStatus.PENDING, invitation.getStatus());
        assertNull(invitation.getRespondedBy());
        assertNull(invitation.getRespondedAt());
    }

    @Test
    @DisplayName("같은 프로젝트의 여러 대기 초대는 한 번의 잠금 조회로 같은 시각에 취소한다")
    void cancelRelatedPendingInvitations_MultipleInvitations_CancelsTogether() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Project project = project(PROJECT_ID, owner);
        ProjectCollaboratorInvitation sent = invitation(project, owner,
                member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com"));
        ProjectCollaboratorInvitation received = invitation(project,
                member(30L, Role.ROLE_USER, "another@test.com"), owner);
        stubCancellation(owner, project, List.of(sent, received));

        // when
        service.cancelRelatedPendingInvitations(OWNER_ID, List.of(PROJECT_ID));

        // then
        assertEquals(ProjectCollaboratorInvitationStatus.CANCELLED, sent.getStatus());
        assertEquals(ProjectCollaboratorInvitationStatus.CANCELLED, received.getStatus());
        assertEquals(sent.getRespondedAt(), received.getRespondedAt());
        assertSame(owner, sent.getRespondedBy());
        assertSame(owner, received.getRespondedBy());
        verify(invitationRepository).findPendingRelatedByProjectIdAndMemberIdForUpdate(PROJECT_ID, OWNER_ID);
        verify(invitationRepository, never()).save(any(ProjectCollaboratorInvitation.class));
    }

    @Test
    @DisplayName("여러 project의 초대는 호출자가 준 잠금 순서대로 잠근다")
    void cancelRelatedPendingInvitations_MultipleProjects_LocksInGivenOrder() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        ProjectCollaboratorInvitation sent = invitation(project(1L, owner), owner, invitee);
        ProjectCollaboratorInvitation received = invitation(project(2L, invitee), invitee, owner);
        when(memberRepository.findByIdForUpdate(OWNER_ID)).thenReturn(Optional.of(owner));
        when(invitationRepository.findPendingRelatedProjectIdsOrderByProjectIdAsc(OWNER_ID))
                .thenReturn(List.of(1L, 2L));
        when(invitationRepository.findPendingRelatedByProjectIdAndMemberIdForUpdate(1L, OWNER_ID))
                .thenReturn(List.of(sent));
        when(invitationRepository.findPendingRelatedByProjectIdAndMemberIdForUpdate(2L, OWNER_ID))
                .thenReturn(List.of(received));

        // when
        service.cancelRelatedPendingInvitations(OWNER_ID, List.of(1L, 2L));

        // then
        InOrder order = inOrder(invitationRepository);
        order.verify(invitationRepository).findPendingRelatedByProjectIdAndMemberIdForUpdate(1L, OWNER_ID);
        order.verify(invitationRepository).findPendingRelatedByProjectIdAndMemberIdForUpdate(2L, OWNER_ID);
        assertEquals(ProjectCollaboratorInvitationStatus.CANCELLED, sent.getStatus());
        assertEquals(ProjectCollaboratorInvitationStatus.CANCELLED, received.getStatus());
        verifyNoInteractions(projectRepository, collaboratorRepository);
    }

    @Test
    @DisplayName("두 번째 project의 초대 잠금 실패를 전파하고 나머지 취소를 중단한다")
    void cancelRelatedPendingInvitations_LockFails_PropagatesAndStopsProcessing() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        ProjectCollaboratorInvitation invitation = invitation(project(1L, owner), owner,
                member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com"));
        when(memberRepository.findByIdForUpdate(OWNER_ID)).thenReturn(Optional.of(owner));
        when(invitationRepository.findPendingRelatedProjectIdsOrderByProjectIdAsc(OWNER_ID))
                .thenReturn(List.of(1L, 2L, 3L));
        when(invitationRepository.findPendingRelatedByProjectIdAndMemberIdForUpdate(1L, OWNER_ID))
                .thenReturn(List.of(invitation));
        PessimisticLockingFailureException failure = new PessimisticLockingFailureException("lock failed");
        when(invitationRepository.findPendingRelatedByProjectIdAndMemberIdForUpdate(2L, OWNER_ID))
                .thenThrow(failure);

        // when
        PessimisticLockingFailureException exception = assertThrows(PessimisticLockingFailureException.class,
                () -> service.cancelRelatedPendingInvitations(OWNER_ID, List.of(1L, 2L, 3L)));

        // then
        assertSame(failure, exception);
        verify(invitationRepository).findPendingRelatedByProjectIdAndMemberIdForUpdate(1L, OWNER_ID);
        verify(invitationRepository, never()).findPendingRelatedByProjectIdAndMemberIdForUpdate(3L, OWNER_ID);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("떠나는 회원이 없거나 비활성이면 관련 초대를 조회하지 않는다")
    void cancelRelatedPendingInvitations_MemberUnavailable_ThrowsMemberNotFound(boolean missing) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        owner.withdraw();
        when(memberRepository.findByIdForUpdate(OWNER_ID))
                .thenReturn(missing ? Optional.empty() : Optional.of(owner));

        // when
        MemberException exception = assertThrows(MemberException.class,
                () -> service.cancelRelatedPendingInvitations(OWNER_ID, List.of(PROJECT_ID)));

        // then
        assertEquals(MemberErrorCode.MEMBER_NOT_FOUND, exception.getCode());
        verifyNoInteractions(invitationRepository, projectRepository, collaboratorRepository);
    }

    @Test
    @DisplayName("회원 잠금 조회 실패를 전파하고 초대를 조회하지 않는다")
    void cancelRelatedPendingInvitations_MemberLockFails_Propagates() {
        // given
        PessimisticLockingFailureException failure = new PessimisticLockingFailureException("lock failed");
        when(memberRepository.findByIdForUpdate(OWNER_ID)).thenThrow(failure);

        // when
        PessimisticLockingFailureException exception = assertThrows(PessimisticLockingFailureException.class,
                () -> service.cancelRelatedPendingInvitations(OWNER_ID, List.of(PROJECT_ID)));

        // then
        assertSame(failure, exception);
        verifyNoInteractions(invitationRepository);
    }

    @Test
    @DisplayName("취소 시각은 초대 잠금을 얻은 이후의 시각으로 기록한다")
    void cancelRelatedPendingInvitations_ClockAdvancesAtLock_RecordsPostLockTime() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Project project = project(PROJECT_ID, owner);
        ProjectCollaboratorInvitation invitation = invitation(project, owner,
                member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com"));
        stubCancellation(owner, project, List.of(invitation));
        LocalDateTime beforeLock = LocalDateTime.of(2026, 9, 28, 12, 0);
        LocalDateTime afterLock = beforeLock.plusMinutes(1);
        try (MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            time.when(LocalDateTime::now).thenReturn(beforeLock);
            when(invitationRepository.findPendingRelatedByProjectIdAndMemberIdForUpdate(PROJECT_ID, OWNER_ID))
                    .thenAnswer(invocation -> {
                        time.when(LocalDateTime::now).thenReturn(afterLock);
                        return List.of(invitation);
                    });

            // when
            service.cancelRelatedPendingInvitations(OWNER_ID, List.of(PROJECT_ID));

            // then
            assertEquals(afterLock, invitation.getRespondedAt());
            assertEquals(ProjectCollaboratorInvitationStatus.CANCELLED, invitation.getStatus());
        }
    }

    private void stubCancellation(
            Member departingMember, Project project, List<ProjectCollaboratorInvitation> invitations
    ) {
        when(memberRepository.findByIdForUpdate(departingMember.getId())).thenReturn(Optional.of(departingMember));
        when(invitationRepository.findPendingRelatedProjectIdsOrderByProjectIdAsc(departingMember.getId()))
                .thenReturn(List.of(project.getId()));
        when(invitationRepository.findPendingRelatedByProjectIdAndMemberIdForUpdate(project.getId(), departingMember.getId()))
                .thenReturn(invitations);
    }

    @ParameterizedTest
    @CsvSource({"10, 20, ROLE_USER", "10, 5, ROLE_USER", "10, 20, ROLE_GUEST", "10, 5, ROLE_GUEST"})
    @DisplayName("초대 발신은 project 다음 두 회원을 ID 순으로 잠그고 잠금 조회한 회원을 저장한다")
    void invite_ActiveLockedMembers_SavesInMemberIdOrder(Long ownerId, Long inviteeId, Role role) {
        // given
        Member owner = member(ownerId, role, "owner@test.com");
        Member identifiedInvitee = member(inviteeId, role, "identified@test.com");
        Member lockedInvitee = member(inviteeId, role, "locked@test.com");
        Project project = project(PROJECT_ID, owner);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(memberQueryService.findByInvitationCode(INVITEE_CODE)).thenReturn(identifiedInvitee);
        stubInviteMemberLocks(owner, lockedInvitee);

        // when
        service.invite(PROJECT_ID, ownerId, INVITEE_CODE, ProjectCollaboratorRole.VIEWER);

        // then
        InOrder order = inOrder(projectRepository, memberRepository, invitationRepository);
        order.verify(projectRepository).findByIdForUpdate(PROJECT_ID);
        order.verify(memberRepository).findByIdForUpdate(Math.min(ownerId, inviteeId));
        order.verify(memberRepository).findByIdForUpdate(Math.max(ownerId, inviteeId));
        var saved = ArgumentCaptor.forClass(ProjectCollaboratorInvitation.class);
        order.verify(invitationRepository).save(saved.capture());
        assertSame(owner, saved.getValue().getInvitedBy());
        assertSame(lockedInvitee, saved.getValue().getInvitee());
        assertEquals(ProjectCollaboratorRole.VIEWER, saved.getValue().getRole());
        assertEquals(ProjectCollaboratorInvitationStatus.PENDING, saved.getValue().getStatus());
    }

    @ParameterizedTest
    @CsvSource({"true, true", "true, false", "false, true", "false, false"})
    @DisplayName("코드 조회 뒤 회원 잠금 결과가 부재·비활성이면 초대를 저장하지 않는다")
    void invite_UnavailableLockedMember_ThrowsExistingError(boolean unavailableOwner, boolean missing) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member identifiedInvitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project(PROJECT_ID, owner)));
        when(memberQueryService.findByInvitationCode(INVITEE_CODE)).thenReturn(identifiedInvitee);
        Long unavailableId = unavailableOwner ? OWNER_ID : INVITEE_ID;
        if (!unavailableOwner) {
            when(memberRepository.findByIdForUpdate(OWNER_ID)).thenReturn(Optional.of(owner));
        }
        when(memberRepository.findByIdForUpdate(unavailableId)).thenReturn(missing ? Optional.empty()
                : Optional.of(member(unavailableId, Role.ROLE_USER, "inactive@test.com", false)));

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> service.invite(PROJECT_ID, OWNER_ID, INVITEE_CODE, ProjectCollaboratorRole.EDITOR));

        // then
        assertEquals(unavailableOwner ? ProjectErrorCode.PROJECT_NOT_FOUND
                : ProjectErrorCode.COLLABORATOR_INVITATION_TARGET_UNAVAILABLE, exception.getCode());
        verify(invitationRepository, never()).save(any(ProjectCollaboratorInvitation.class));
    }

    @ParameterizedTest
    @ValueSource(longs = {10L, 20L})
    @DisplayName("발신 회원 잠금 실패를 전파하고 대기 초대를 저장하지 않는다")
    void invite_MemberLockFailure_PropagatesWithoutSaving(Long failingId) {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        var failure = new PessimisticLockingFailureException("member lock failed");
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project(PROJECT_ID, owner)));
        when(memberQueryService.findByInvitationCode(INVITEE_CODE)).thenReturn(invitee);
        if (failingId.equals(INVITEE_ID)) {
            when(memberRepository.findByIdForUpdate(OWNER_ID)).thenReturn(Optional.of(owner));
        }
        when(memberRepository.findByIdForUpdate(failingId)).thenThrow(failure);

        // when
        var exception = assertThrows(PessimisticLockingFailureException.class,
                () -> service.invite(PROJECT_ID, OWNER_ID, INVITEE_CODE, ProjectCollaboratorRole.EDITOR));

        // then
        assertSame(failure, exception);
        verify(invitationRepository, never()).save(any(ProjectCollaboratorInvitation.class));
    }

    @Test
    @DisplayName("본인을 초대하는 요청은 회원 잠금 전에 기존 오류로 거부한다")
    void invite_SelfTarget_ThrowsOwnerCannotBeCollaborator() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project(PROJECT_ID, owner)));
        when(memberQueryService.findByInvitationCode(INVITEE_CODE)).thenReturn(owner);

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> service.invite(PROJECT_ID, OWNER_ID, INVITEE_CODE, ProjectCollaboratorRole.EDITOR));

        // then
        assertEquals(ProjectErrorCode.OWNER_CANNOT_BE_COLLABORATOR, exception.getCode());
        verifyNoInteractions(memberRepository, invitationRepository, collaboratorRepository);
    }

    @Test
    @DisplayName("회원 잠금 대기 이후 시각으로 중복 초대와 24시간 만료를 계산한다")
    void invite_TimeChangesDuringMemberLock_UsesPostLockTime() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 29, 0, 0);
        LocalDateTime lockedAt = startedAt.plusMinutes(5);
        AtomicReference<LocalDateTime> currentTime = new AtomicReference<>(startedAt);
        Member owner = member(OWNER_ID, Role.ROLE_USER, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_USER, "invitee@test.com");
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project(PROJECT_ID, owner)));
        when(memberQueryService.findByInvitationCode(INVITEE_CODE)).thenReturn(invitee);
        when(memberRepository.findByIdForUpdate(OWNER_ID)).thenReturn(Optional.of(owner));
        when(memberRepository.findByIdForUpdate(INVITEE_ID)).thenAnswer(invocation -> {
            currentTime.set(lockedAt);
            return Optional.of(invitee);
        });
        try (MockedStatic<LocalDateTime> clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(LocalDateTime::now).thenAnswer(invocation -> currentTime.get());

            // when
            service.invite(PROJECT_ID, OWNER_ID, INVITEE_CODE, ProjectCollaboratorRole.EDITOR);

            // then
            var saved = ArgumentCaptor.forClass(ProjectCollaboratorInvitation.class);
            verify(invitationRepository).save(saved.capture());
            assertEquals(lockedAt.plusHours(24), saved.getValue().getExpiresAt());
            verify(invitationRepository).existsByProjectIdAndInviteeIdAndStatusAndExpiresAtAfter(
                    PROJECT_ID, INVITEE_ID, ProjectCollaboratorInvitationStatus.PENDING, lockedAt);
        }
    }

    private void stubInviteMemberLocks(Member owner, Member invitee) {
        when(memberRepository.findByIdForUpdate(owner.getId())).thenReturn(Optional.of(owner));
        when(memberRepository.findByIdForUpdate(invitee.getId())).thenReturn(Optional.of(invitee));
    }

    private void stubResponseLocks(ProjectCollaboratorInvitation invitation) {
        when(invitationRepository.findProjectIdByIdAndInviteeId(100L, INVITEE_ID))
                .thenReturn(Optional.of(PROJECT_ID));
        when(projectRepository.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(invitation.getProject()));
        when(memberRepository.findByIdForUpdate(INVITEE_ID))
                .thenReturn(Optional.of(invitation.getInvitee()));
        when(invitationRepository.findByIdAndInviteeIdForUpdate(100L, INVITEE_ID))
                .thenReturn(Optional.of(invitation));
    }

    private void verifyResponseLockOrder() {
        InOrder order = inOrder(invitationRepository, projectRepository, memberRepository);
        order.verify(invitationRepository).findProjectIdByIdAndInviteeId(100L, INVITEE_ID);
        order.verify(projectRepository).findByIdForUpdate(PROJECT_ID);
        order.verify(memberRepository).findByIdForUpdate(INVITEE_ID);
        order.verify(invitationRepository).findByIdAndInviteeIdForUpdate(100L, INVITEE_ID);
    }

    private void respond(boolean accept) {
        if (accept) {
            service.accept(100L, INVITEE_ID);
        } else {
            service.decline(100L, INVITEE_ID);
        }
    }

    private Member member(Long id, Role role, String email) {
        return member(id, role, email, true);
    }

    private Member member(Long id, Role role, String email, Boolean active) {
        Member member = Member.builder()
                .email(email)
                .password("encodedPassword")
                .nickname("member")
                .role(role)
                .isActive(active)
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Project project(Long id, Member owner) {
        Project project = Project.builder()
                .title("project")
                .member(owner)
                .build();
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }

    private ProjectCollaboratorInvitation invitation(Project project, Member owner, Member invitee) {
        return ProjectCollaboratorInvitation.builder()
                .project(project)
                .invitedBy(owner)
                .invitee(invitee)
                .role(ProjectCollaboratorRole.EDITOR)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
    }
}
