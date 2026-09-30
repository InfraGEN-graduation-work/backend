package com.infragen.infragen.domain.project.entity;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorInvitationStatus;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectCollaboratorInvitationTest {
    private static final LocalDateTime PROCESSED_AT = LocalDateTime.of(2026, 9, 28, 12, 0);
    private static final LocalDateTime EXPIRES_AT = PROCESSED_AT.plusHours(24);

    @Test
    @DisplayName("대기 초대를 취소하면 취소자와 처리 시각을 기록한다")
    void cancel_PendingInvitation_RecordsCancellation() {
        // given
        Member owner = member("owner");
        Member invitee = member("invitee");
        ProjectCollaboratorInvitation invitation = invitation(owner, invitee, EXPIRES_AT);
        Project project = invitation.getProject();

        // when
        invitation.cancel(owner, PROCESSED_AT);

        // then
        assertAll(
                () -> assertEquals(ProjectCollaboratorInvitationStatus.CANCELLED, invitation.getStatus()),
                () -> assertSame(owner, invitation.getRespondedBy()),
                () -> assertEquals(PROCESSED_AT, invitation.getRespondedAt()),
                () -> assertSame(project, invitation.getProject()),
                () -> assertSame(owner, invitation.getInvitedBy()),
                () -> assertSame(invitee, invitation.getInvitee()),
                () -> assertEquals(ProjectCollaboratorRole.EDITOR, invitation.getRole()),
                () -> assertEquals(EXPIRES_AT, invitation.getExpiresAt())
        );
    }

    @Test
    @DisplayName("만료 시각이 지난 대기 초대도 취소 상태와 처리 시각을 기록한다")
    void cancel_ExpiredPendingInvitation_RecordsCancellation() {
        // given
        Member owner = member("owner");
        LocalDateTime expiredAt = PROCESSED_AT.minusHours(1);
        ProjectCollaboratorInvitation invitation = invitation(owner, member("invitee"), expiredAt);

        // when
        invitation.cancel(owner, PROCESSED_AT);

        // then
        assertAll(
                () -> assertEquals(ProjectCollaboratorInvitationStatus.CANCELLED, invitation.getStatus()),
                () -> assertEquals(PROCESSED_AT, invitation.getRespondedAt()),
                () -> assertEquals(expiredAt, invitation.getExpiresAt())
        );
    }

    @ParameterizedTest
    @EnumSource(value = ProjectCollaboratorInvitationStatus.class, names = {"ACCEPTED", "DECLINED", "CANCELLED"})
    @DisplayName("이미 처리된 초대를 취소해도 상태와 처리 기록은 유지한다")
    void cancel_ProcessedInvitation_PreservesHistory(ProjectCollaboratorInvitationStatus status) {
        // given
        Member owner = member("owner");
        Member invitee = member("invitee");
        ProjectCollaboratorInvitation invitation = invitation(owner, invitee, EXPIRES_AT);
        process(invitation, status, owner, invitee);
        Member previousProcessor = invitation.getRespondedBy();

        // when
        invitation.cancel(member("another-processor"), PROCESSED_AT.plusHours(1));

        // then
        assertAll(
                () -> assertEquals(status, invitation.getStatus()),
                () -> assertSame(previousProcessor, invitation.getRespondedBy()),
                () -> assertEquals(PROCESSED_AT, invitation.getRespondedAt()),
                () -> assertEquals(EXPIRES_AT, invitation.getExpiresAt())
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("취소자 또는 시각이 없으면 대기 초대를 변경하지 않는다")
    void cancel_MissingProcessorOrTime_ThrowsUnavailable(boolean missingProcessor) {
        // given
        Member owner = member("owner");
        ProjectCollaboratorInvitation invitation = invitation(owner, member("invitee"), EXPIRES_AT);

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> invitation.cancel(
                missingProcessor ? null : owner, missingProcessor ? PROCESSED_AT : null));

        // then
        assertAll(
                () -> assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode()),
                () -> assertEquals(ProjectCollaboratorInvitationStatus.PENDING, invitation.getStatus()),
                () -> assertNull(invitation.getRespondedBy()),
                () -> assertNull(invitation.getRespondedAt())
        );
    }

    @ParameterizedTest
    @CsvSource({"ACCEPTED, true", "ACCEPTED, false", "DECLINED, true", "DECLINED, false",
            "CANCELLED, true", "CANCELLED, false"})
    @DisplayName("완료된 초대는 직접 수락·거절을 호출해도 기존 이력을 덮어쓰지 않는다")
    void respond_ProcessedInvitation_ThrowsUnavailable(
            ProjectCollaboratorInvitationStatus status, boolean accept
    ) {
        // given
        Member owner = member("owner");
        Member invitee = member("invitee");
        ProjectCollaboratorInvitation invitation = invitation(owner, invitee, EXPIRES_AT);
        process(invitation, status, owner, invitee);
        Member previousProcessor = invitation.getRespondedBy();

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> {
            if (accept) {
                invitation.accept(invitee, PROCESSED_AT.plusHours(1));
            } else {
                invitation.decline(invitee, PROCESSED_AT.plusHours(1));
            }
        });

        // then
        assertAll(
                () -> assertEquals(ProjectErrorCode.COLLABORATOR_INVITATION_UNAVAILABLE, exception.getCode()),
                () -> assertEquals(status, invitation.getStatus()),
                () -> assertSame(previousProcessor, invitation.getRespondedBy()),
                () -> assertEquals(PROCESSED_AT, invitation.getRespondedAt())
        );
    }

    private void process(
            ProjectCollaboratorInvitation invitation,
            ProjectCollaboratorInvitationStatus status,
            Member owner,
            Member invitee
    ) {
        switch (status) {
            case ACCEPTED -> invitation.accept(invitee, PROCESSED_AT);
            case DECLINED -> invitation.decline(invitee, PROCESSED_AT);
            case CANCELLED -> invitation.cancel(owner, PROCESSED_AT);
            default -> throw new IllegalStateException("이미 처리된 상태만 fixture로 사용한다.");
        }
    }

    private Member member(String nickname) {
        return Member.builder()
                .email(nickname + "@test.com")
                .password("encoded")
                .nickname(nickname)
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();
    }

    private ProjectCollaboratorInvitation invitation(Member owner, Member invitee, LocalDateTime expiresAt) {
        return ProjectCollaboratorInvitation.builder()
                .project(Project.builder().title("project").member(owner).build())
                .invitedBy(owner)
                .invitee(invitee)
                .role(ProjectCollaboratorRole.EDITOR)
                .expiresAt(expiresAt)
                .build();
    }
}
