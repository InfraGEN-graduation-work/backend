package com.infragen.infragen.domain.project.converter;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaboratorInvitation;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorInvitationStatus;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import jakarta.persistence.Tuple;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.hibernate.jpa.spi.NativeQueryTupleTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ProjectCollaboratorInvitationConverterTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 18, 10, 0, 0, 123456000);
    private static final LocalDateTime RESPONDED_AT = LocalDateTime.of(2026, 9, 18, 11, 0);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 9, 19, 10, 0);

    @ParameterizedTest
    @MethodSource("numericIds")
    @DisplayName("native 발신 값의 숫자 ID·enum·Timestamp를 기존 응답 필드로 변환한다")
    void toSentItem_NativeValues_MapsOwnerView(Number id) {
        // given
        Tuple invitation = invitation(id, "invitee", "EDITOR", Timestamp.valueOf(CREATED_AT),
                Timestamp.valueOf(EXPIRES_AT), Timestamp.valueOf(RESPONDED_AT));

        // when
        ProjectCollaboratorInvitationResDTO.SentItem item = ProjectCollaboratorInvitationConverter.toSentItem(
                invitation, ProjectCollaboratorInvitationResDTO.InvitationStatus.CANCELLED);

        // then
        assertAll(
                () -> assertEquals(101L, item.invitationId()),
                () -> assertEquals("invitee", item.inviteeNickname()),
                () -> assertEquals(ProjectCollaboratorRole.EDITOR, item.role()),
                () -> assertEquals(ProjectCollaboratorInvitationResDTO.InvitationStatus.CANCELLED, item.status()),
                () -> assertEquals(CREATED_AT, item.createdAt()),
                () -> assertEquals(EXPIRES_AT, item.expiresAt()),
                () -> assertEquals(RESPONDED_AT, item.respondedAt()),
                () -> assertEquals(List.of(item), ProjectCollaboratorInvitationConverter.toSentList(List.of(item)).invitations())
        );
    }

    @Test
    @DisplayName("발신 응답은 탈퇴회원 표시와 미응답 null·LocalDateTime을 그대로 유지한다")
    void toSentItem_InactiveDisplayAndNullResponse_PreservesValues() {
        // given
        Tuple invitation = invitation(101L, "탈퇴회원", "VIEWER", CREATED_AT, EXPIRES_AT, null);

        // when
        ProjectCollaboratorInvitationResDTO.SentItem item = ProjectCollaboratorInvitationConverter.toSentItem(
                invitation, ProjectCollaboratorInvitationResDTO.InvitationStatus.EXPIRED);

        // then
        assertAll(
                () -> assertEquals("탈퇴회원", item.inviteeNickname()),
                () -> assertEquals(ProjectCollaboratorRole.VIEWER, item.role()),
                () -> assertEquals(ProjectCollaboratorInvitationResDTO.InvitationStatus.EXPIRED, item.status()),
                () -> assertEquals(CREATED_AT, item.createdAt()),
                () -> assertEquals(EXPIRES_AT, item.expiresAt()),
                () -> assertNull(item.respondedAt())
        );
    }

    @Test
    @DisplayName("수신 응답은 프로젝트와 탈퇴 초대자의 표시 정보·시각을 유지한다")
    void toReceivedItem_InactiveInviter_MapsRecipientView() {
        // given
        Tuple invitation = invitation(101L, "탈퇴회원", "VIEWER", Timestamp.valueOf(CREATED_AT), EXPIRES_AT, null);

        // when
        ProjectCollaboratorInvitationResDTO.ReceivedItem item = ProjectCollaboratorInvitationConverter.toReceivedItem(
                invitation, ProjectCollaboratorInvitationResDTO.InvitationStatus.ACCEPTED);

        // then
        assertAll(
                () -> assertEquals(101L, item.invitationId()),
                () -> assertEquals("project", item.projectTitle()),
                () -> assertEquals("탈퇴회원", item.inviterNickname()),
                () -> assertEquals(ProjectCollaboratorRole.VIEWER, item.role()),
                () -> assertEquals(ProjectCollaboratorInvitationResDTO.InvitationStatus.ACCEPTED, item.status()),
                () -> assertEquals(CREATED_AT, item.createdAt()),
                () -> assertEquals(EXPIRES_AT, item.expiresAt()),
                () -> assertEquals(List.of(item), ProjectCollaboratorInvitationConverter.toReceivedList(List.of(item)).invitations())
        );
    }

    @Test
    @DisplayName("수신 응답은 활성 초대자의 닉네임을 유지한다")
    void toReceivedItem_ActiveInviter_PreservesNickname() {
        // given
        Tuple invitation = invitation(101L, "active-owner", "EDITOR", CREATED_AT, EXPIRES_AT, null);

        // when
        ProjectCollaboratorInvitationResDTO.ReceivedItem item = ProjectCollaboratorInvitationConverter.toReceivedItem(
                invitation, ProjectCollaboratorInvitationResDTO.InvitationStatus.PENDING);

        // then
        assertEquals("active-owner", item.inviterNickname());
    }

    @Test
    @DisplayName("발신 생성 변환은 기존 회원 관계와 초기 PENDING 상태를 유지한다")
    void toEntity_ActiveMembers_PreservesCreationContract() {
        // given
        Member owner = member("owner");
        Member invitee = member("invitee");
        Project project = Project.builder().title("project").member(owner).build();

        // when
        ProjectCollaboratorInvitation invitation = ProjectCollaboratorInvitationConverter.toEntity(
                project, owner, invitee, ProjectCollaboratorRole.EDITOR, EXPIRES_AT);

        // then
        assertAll(
                () -> assertSame(project, invitation.getProject()),
                () -> assertSame(owner, invitation.getInvitedBy()),
                () -> assertSame(invitee, invitation.getInvitee()),
                () -> assertEquals(ProjectCollaboratorRole.EDITOR, invitation.getRole()),
                () -> assertEquals(ProjectCollaboratorInvitationStatus.PENDING, invitation.getStatus()),
                () -> assertEquals(EXPIRES_AT, invitation.getExpiresAt())
        );
    }

    private static Stream<Number> numericIds() {
        return Stream.of(101L, 101, BigInteger.valueOf(101L));
    }

    private Tuple invitation(Number id, String nickname, String role, Object createdAt,
                             Object expiresAt, Object respondedAt) {
        return NativeQueryTupleTransformer.INSTANCE.transformTuple(
                new Object[]{id, "project", nickname, role, createdAt, expiresAt, respondedAt},
                new String[]{"invitationId", "projectTitle", "memberNickname", "role", "createdAt", "expiresAt", "respondedAt"}
        );
    }

    private Member member(String nickname) {
        return Member.builder().email(nickname + "@test.com").nickname(nickname)
                .password("encodedPassword").role(Role.ROLE_USER).isActive(true).build();
    }
}
