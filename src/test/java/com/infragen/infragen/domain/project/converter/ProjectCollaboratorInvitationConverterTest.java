package com.infragen.infragen.domain.project.converter;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaboratorInvitation;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectCollaboratorInvitationConverterTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 18, 10, 0);
    private static final LocalDateTime RESPONDED_AT = LocalDateTime.of(2026, 9, 18, 11, 0);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 9, 19, 10, 0);

    @Test
    @DisplayName("발신 초대 응답에는 회원 ID와 초대코드 없이 표시 정보를 담는다")
    void toSentItem_Invitation_MapsOwnerView() {
        // given
        ProjectCollaboratorInvitation invitation = invitation();

        // when
        ProjectCollaboratorInvitationResDTO.SentItem item =
                ProjectCollaboratorInvitationConverter.toSentItem(
                        invitation,
                        ProjectCollaboratorInvitationResDTO.InvitationStatus.EXPIRED
                );

        // then
        assertAll(
                () -> assertEquals(101L, item.invitationId()),
                () -> assertEquals("invitee", item.inviteeNickname()),
                () -> assertEquals(ProjectCollaboratorRole.EDITOR, item.role()),
                () -> assertEquals(ProjectCollaboratorInvitationResDTO.InvitationStatus.EXPIRED, item.status()),
                () -> assertEquals(CREATED_AT, item.createdAt()),
                () -> assertEquals(EXPIRES_AT, item.expiresAt()),
                () -> assertEquals(RESPONDED_AT, item.respondedAt())
        );
        assertEquals(List.of(item), ProjectCollaboratorInvitationConverter.toSentList(List.of(item)).invitations());
    }

    @Test
    @DisplayName("받은 초대 응답에는 프로젝트와 초대자 정보를 담는다")
    void toReceivedItem_Invitation_MapsRecipientView() {
        // given
        ProjectCollaboratorInvitation invitation = invitation();

        // when
        ProjectCollaboratorInvitationResDTO.ReceivedItem item =
                ProjectCollaboratorInvitationConverter.toReceivedItem(
                        invitation,
                        ProjectCollaboratorInvitationResDTO.InvitationStatus.ACCEPTED
                );

        // then
        assertAll(
                () -> assertEquals(101L, item.invitationId()),
                () -> assertEquals("project", item.projectTitle()),
                () -> assertEquals("owner", item.inviterNickname()),
                () -> assertEquals(ProjectCollaboratorRole.EDITOR, item.role()),
                () -> assertEquals(ProjectCollaboratorInvitationResDTO.InvitationStatus.ACCEPTED, item.status()),
                () -> assertEquals(CREATED_AT, item.createdAt()),
                () -> assertEquals(EXPIRES_AT, item.expiresAt())
        );
        assertEquals(
                List.of(item),
                ProjectCollaboratorInvitationConverter.toReceivedList(List.of(item)).invitations()
        );
    }

    private ProjectCollaboratorInvitation invitation() {
        Member owner = member("owner@test.com", "owner");
        Member invitee = member("invitee@test.com", "invitee");
        Project project = Project.builder()
                .title("project")
                .member(owner)
                .build();
        ProjectCollaboratorInvitation invitation = ProjectCollaboratorInvitation.builder()
                .project(project)
                .invitedBy(owner)
                .invitee(invitee)
                .role(ProjectCollaboratorRole.EDITOR)
                .expiresAt(EXPIRES_AT)
                .build();
        ReflectionTestUtils.setField(invitation, "id", 101L);
        ReflectionTestUtils.setField(invitation, "createdAt", CREATED_AT);
        invitation.accept(invitee, RESPONDED_AT);
        return invitation;
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
}
