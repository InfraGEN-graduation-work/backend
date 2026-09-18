package com.infragen.infragen.domain.project.service.query;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaboratorInvitation;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorInvitationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCollaboratorInvitationQueryServiceTest {
    private static final Long PROJECT_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long INVITEE_ID = 20L;

    @Mock
    private ProjectQueryService projectQueryService;

    @Mock
    private ProjectCollaboratorInvitationRepository invitationRepository;

    @InjectMocks
    private ProjectCollaboratorInvitationQueryService service;

    @Test
    @DisplayName("발신 초대 목록에서 만료된 대기 초대를 EXPIRED로 표시한다")
    void getSentInvitations_ExpiredPending_ShowsExpiredStatus() {
        // given
        Member owner = member("owner@test.com", "owner");
        Member invitee = member("invitee@test.com", "invitee");
        Project project = project(owner);
        ProjectCollaboratorInvitation expired = invitation(
                101L,
                project,
                owner,
                invitee,
                LocalDateTime.now().minusMinutes(1)
        );
        when(projectQueryService.getOwnedProject(PROJECT_ID, OWNER_ID)).thenReturn(project);
        when(invitationRepository.findAllByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID))
                .thenReturn(List.of(expired));

        // when
        ProjectCollaboratorInvitationResDTO.SentList result =
                service.getSentInvitations(PROJECT_ID, OWNER_ID);

        // then
        assertEquals(1, result.invitations().size());
        assertEquals(
                ProjectCollaboratorInvitationResDTO.InvitationStatus.EXPIRED,
                result.invitations().get(0).status()
        );
        assertEquals("invitee", result.invitations().get(0).inviteeNickname());
        verify(projectQueryService).getOwnedProject(PROJECT_ID, OWNER_ID);
    }

    @Test
    @DisplayName("받은 초대 목록은 요청한 상태만 반환한다")
    void getReceivedInvitations_PendingFilter_ReturnsUnexpiredPendingOnly() {
        // given
        Member owner = member("owner@test.com", "owner");
        Member invitee = member("invitee@test.com", "invitee");
        Project project = project(owner);
        ProjectCollaboratorInvitation pending = invitation(
                101L,
                project,
                owner,
                invitee,
                LocalDateTime.now().plusHours(1)
        );
        ProjectCollaboratorInvitation expired = invitation(
                102L,
                project,
                owner,
                invitee,
                LocalDateTime.now().minusMinutes(1)
        );
        when(invitationRepository.findAllByInviteeIdOrderByCreatedAtDescIdDesc(INVITEE_ID))
                .thenReturn(List.of(pending, expired));

        // when
        ProjectCollaboratorInvitationResDTO.ReceivedList result = service.getReceivedInvitations(
                INVITEE_ID,
                ProjectCollaboratorInvitationResDTO.InvitationStatus.PENDING
        );

        // then
        assertEquals(1, result.invitations().size());
        assertEquals(101L, result.invitations().get(0).invitationId());
        assertEquals("project", result.invitations().get(0).projectTitle());
        assertEquals("owner", result.invitations().get(0).inviterNickname());
    }

    @Test
    @DisplayName("받은 초대 목록에서 EXPIRED 상태를 조회할 수 있다")
    void getReceivedInvitations_ExpiredFilter_ReturnsExpiredInvitation() {
        // given
        Member owner = member("owner@test.com", "owner");
        Member invitee = member("invitee@test.com", "invitee");
        Project project = project(owner);
        ProjectCollaboratorInvitation expired = invitation(
                102L,
                project,
                owner,
                invitee,
                LocalDateTime.now().minusMinutes(1)
        );
        when(invitationRepository.findAllByInviteeIdOrderByCreatedAtDescIdDesc(INVITEE_ID))
                .thenReturn(List.of(expired));

        // when
        ProjectCollaboratorInvitationResDTO.ReceivedList result = service.getReceivedInvitations(
                INVITEE_ID,
                ProjectCollaboratorInvitationResDTO.InvitationStatus.EXPIRED
        );

        // then
        assertEquals(1, result.invitations().size());
        assertEquals(
                ProjectCollaboratorInvitationResDTO.InvitationStatus.EXPIRED,
                result.invitations().get(0).status()
        );
    }

    private Member member(String email, String nickname) {
        return Member.builder()
                .email(email)
                .password("encodedPassword")
                .nickname(nickname)
                .role(Role.ROLE_GUEST)
                .isActive(true)
                .build();
    }

    private Project project(Member owner) {
        return Project.builder()
                .title("project")
                .member(owner)
                .build();
    }

    private ProjectCollaboratorInvitation invitation(
            Long invitationId,
            Project project,
            Member owner,
            Member invitee,
            LocalDateTime expiresAt
    ) {
        ProjectCollaboratorInvitation invitation = ProjectCollaboratorInvitation.builder()
                .project(project)
                .invitedBy(owner)
                .invitee(invitee)
                .role(ProjectCollaboratorRole.EDITOR)
                .expiresAt(expiresAt)
                .build();
        ReflectionTestUtils.setField(invitation, "id", invitationId);
        return invitation;
    }
}
