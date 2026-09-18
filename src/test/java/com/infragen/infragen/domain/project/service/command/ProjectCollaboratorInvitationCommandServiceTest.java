package com.infragen.infragen.domain.project.service.command;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        when(invitationRepository.findByIdAndInviteeIdForUpdate(100L, INVITEE_ID))
                .thenReturn(Optional.of(invitation));
        when(collaboratorRepository.existsByProjectIdAndMemberId(PROJECT_ID, INVITEE_ID))
                .thenReturn(false);

        // when
        service.accept(100L, INVITEE_ID);

        // then
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
    @DisplayName("대상 회원이 거절하면 초대만 DECLINED로 변경한다")
    void decline_PendingInvitation_ChangesStatusWithoutCollaborator() {
        // given
        Member owner = member(OWNER_ID, Role.ROLE_GUEST, "owner@test.com");
        Member invitee = member(INVITEE_ID, Role.ROLE_GUEST, "invitee@test.com");
        Project project = project(PROJECT_ID, owner);
        ProjectCollaboratorInvitation invitation = invitation(project, owner, invitee);
        when(invitationRepository.findByIdAndInviteeIdForUpdate(100L, INVITEE_ID))
                .thenReturn(Optional.of(invitation));

        // when
        service.decline(100L, INVITEE_ID);

        // then
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
        when(invitationRepository.findByIdAndInviteeIdForUpdate(100L, INVITEE_ID))
                .thenReturn(Optional.of(invitation));

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
        when(invitationRepository.findByIdAndInviteeIdForUpdate(100L, INVITEE_ID))
                .thenReturn(Optional.of(invitation));
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

    private Member member(Long id, Role role, String email) {
        Member member = Member.builder()
                .email(email)
                .password("encodedPassword")
                .nickname("member")
                .role(role)
                .isActive(true)
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
