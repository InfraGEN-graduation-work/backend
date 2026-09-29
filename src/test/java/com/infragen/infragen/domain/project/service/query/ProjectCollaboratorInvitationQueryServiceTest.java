package com.infragen.infragen.domain.project.service.query;

import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO.InvitationStatus;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorInvitationRepository;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.jpa.spi.NativeQueryTupleTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCollaboratorInvitationQueryServiceTest {
    private static final Long PROJECT_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long INVITEE_ID = 20L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 28, 10, 0);
    private static final LocalDateTime RESPONDED_AT = LocalDateTime.of(2026, 9, 28, 11, 0);
    private static final LocalDateTime EXPIRED_AT = LocalDateTime.of(2020, 1, 1, 0, 0);
    private static final LocalDateTime FUTURE_AT = LocalDateTime.of(2100, 1, 1, 0, 0);

    @Mock
    private ProjectQueryService projectQueryService;
    @Mock
    private ProjectCollaboratorInvitationRepository invitationRepository;
    @InjectMocks
    private ProjectCollaboratorInvitationQueryService service;

    @Test
    @DisplayName("현재 owner는 탈퇴한 대상의 초대 이력과 취소 시각을 조회한다")
    void getSentInvitations_InactiveInvitee_PreservesHistoryAndCancellation() {
        // given
        Tuple cancelled = invitation(103L, "탈퇴회원", "CANCELLED", EXPIRED_AT, RESPONDED_AT);
        when(invitationRepository.findSentHistoryByProjectId(PROJECT_ID)).thenReturn(List.of(cancelled));

        // when
        ProjectCollaboratorInvitationResDTO.SentList result = service.getSentInvitations(PROJECT_ID, OWNER_ID);

        // then
        assertAll(
                () -> assertEquals(1, result.invitations().size()),
                () -> assertEquals("탈퇴회원", result.invitations().get(0).inviteeNickname()),
                () -> assertEquals(InvitationStatus.CANCELLED, result.invitations().get(0).status()),
                () -> assertEquals(RESPONDED_AT, result.invitations().get(0).respondedAt())
        );
        verify(projectQueryService).getOwnedProject(PROJECT_ID, OWNER_ID);
    }

    @Test
    @DisplayName("현재 owner가 아니면 발신 이력 조회를 실행하지 않는다")
    void getSentInvitations_NotOwner_RejectsBeforeHistoryRead() {
        // given
        when(projectQueryService.getOwnedProject(PROJECT_ID, OWNER_ID))
                .thenThrow(new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> service.getSentInvitations(PROJECT_ID, OWNER_ID));

        // then
        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verify(invitationRepository, never()).findSentHistoryByProjectId(PROJECT_ID);
    }

    @Test
    @DisplayName("발신 목록은 조회 순서를 유지하며 만료된 PENDING만 EXPIRED로 표시한다")
    void getSentInvitations_MixedStatuses_PreservesOrderAndStatus() {
        // given
        when(invitationRepository.findSentHistoryByProjectId(PROJECT_ID)).thenReturn(allStatuses());

        // when
        ProjectCollaboratorInvitationResDTO.SentList result = service.getSentInvitations(PROJECT_ID, OWNER_ID);

        // then
        assertAll(
                () -> assertEquals(List.of(105L, 104L, 103L, 102L, 101L),
                        result.invitations().stream().map(ProjectCollaboratorInvitationResDTO.SentItem::invitationId).toList()),
                () -> assertEquals(List.of(InvitationStatus.CANCELLED, InvitationStatus.DECLINED,
                        InvitationStatus.ACCEPTED, InvitationStatus.EXPIRED, InvitationStatus.PENDING),
                        result.invitations().stream().map(ProjectCollaboratorInvitationResDTO.SentItem::status).toList()),
                () -> assertEquals("active", result.invitations().get(4).inviteeNickname()),
                () -> assertEquals(null, result.invitations().get(4).respondedAt())
        );
    }

    @ParameterizedTest
    @EnumSource(InvitationStatus.class)
    @DisplayName("수신 상태 필터는 탈퇴 초대자의 이력에도 동일하게 적용된다")
    void getReceivedInvitations_StatusFilter_ReturnsMatchingHistory(InvitationStatus filter) {
        // given
        when(invitationRepository.findReceivedHistoryByInviteeId(INVITEE_ID)).thenReturn(allStatuses());

        // when
        ProjectCollaboratorInvitationResDTO.ReceivedList result = service.getReceivedInvitations(INVITEE_ID, filter);

        // then
        assertAll(
                () -> assertEquals(1, result.invitations().size()),
                () -> assertEquals(filter, result.invitations().get(0).status()),
                () -> assertEquals("project", result.invitations().get(0).projectTitle()),
                () -> assertEquals(filter == InvitationStatus.PENDING ? "active" : "탈퇴회원",
                        result.invitations().get(0).inviterNickname())
        );
        verify(invitationRepository).findReceivedHistoryByInviteeId(INVITEE_ID);
    }

    @Test
    @DisplayName("수신 필터를 생략하면 모든 상태를 조회 순서대로 반환한다")
    void getReceivedInvitations_NoFilter_PreservesAllHistoryAndOrder() {
        // given
        when(invitationRepository.findReceivedHistoryByInviteeId(INVITEE_ID)).thenReturn(allStatuses());

        // when
        ProjectCollaboratorInvitationResDTO.ReceivedList result = service.getReceivedInvitations(INVITEE_ID, null);

        // then
        assertEquals(List.of(105L, 104L, 103L, 102L, 101L), result.invitations().stream()
                .map(ProjectCollaboratorInvitationResDTO.ReceivedItem::invitationId).toList());
    }

    @Test
    @DisplayName("발신 이력이 없으면 빈 목록을 반환한다")
    void getSentInvitations_NoHistory_ReturnsEmptyList() {
        // given
        when(invitationRepository.findSentHistoryByProjectId(PROJECT_ID)).thenReturn(List.of());

        // when
        ProjectCollaboratorInvitationResDTO.SentList result = service.getSentInvitations(PROJECT_ID, OWNER_ID);

        // then
        assertTrue(result.invitations().isEmpty());
    }

    @Test
    @DisplayName("수신 이력이 없으면 빈 목록을 반환한다")
    void getReceivedInvitations_NoHistory_ReturnsEmptyList() {
        // given
        when(invitationRepository.findReceivedHistoryByInviteeId(INVITEE_ID)).thenReturn(List.of());

        // when
        ProjectCollaboratorInvitationResDTO.ReceivedList result = service.getReceivedInvitations(INVITEE_ID, null);

        // then
        assertTrue(result.invitations().isEmpty());
    }

    private List<Tuple> allStatuses() {
        return List.of(
                invitation(105L, "탈퇴회원", "CANCELLED", EXPIRED_AT, RESPONDED_AT),
                invitation(104L, "탈퇴회원", "DECLINED", EXPIRED_AT, RESPONDED_AT),
                invitation(103L, "탈퇴회원", "ACCEPTED", EXPIRED_AT, RESPONDED_AT),
                invitation(102L, "탈퇴회원", "PENDING", EXPIRED_AT, null),
                invitation(101L, "active", "PENDING", FUTURE_AT, null)
        );
    }

    private Tuple invitation(Long id, String nickname, String status, LocalDateTime expiresAt,
                             LocalDateTime respondedAt) {
        return NativeQueryTupleTransformer.INSTANCE.transformTuple(
                new Object[]{id, "project", nickname, "EDITOR", status, Timestamp.valueOf(CREATED_AT),
                        Timestamp.valueOf(expiresAt), respondedAt == null ? null : Timestamp.valueOf(respondedAt)},
                new String[]{"invitationId", "projectTitle", "memberNickname", "role", "status",
                        "createdAt", "expiresAt", "respondedAt"}
        );
    }
}
