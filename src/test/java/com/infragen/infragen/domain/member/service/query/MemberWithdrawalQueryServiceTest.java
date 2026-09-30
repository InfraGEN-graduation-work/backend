package com.infragen.infragen.domain.member.service.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.enums.WithdrawalProjectOutcome;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.repository.projection.OwnedProjectWithdrawalPreview;

@ExtendWith(MockitoExtension.class)
class MemberWithdrawalQueryServiceTest {
    private static final Long MEMBER_ID = 1L;

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private MemberWithdrawalQueryService service;

    @Test
    @DisplayName("getWithdrawalPreview_활성 일반 회원_소유 프로젝트별 승계와 삭제를 반환한다")
    void getWithdrawalPreview_ActiveRegularMember_ReturnsOutcomes() {
        // given
        OwnedProjectWithdrawalPreview succession = preview(10L, "승계", true);
        OwnedProjectWithdrawalPreview deletion = preview(20L, "삭제", false);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(Role.ROLE_USER, true)));
        when(projectRepository.findOwnedProjectWithdrawalPreviewsByMemberId(MEMBER_ID))
                .thenReturn(List.of(succession, deletion));

        // when
        MemberResDTO.WithdrawalPreview response = service.getWithdrawalPreview(MEMBER_ID);

        // then
        assertAll(
                () -> assertEquals(List.of(10L, 20L),
                        response.ownedProjects().stream().map(MemberResDTO.WithdrawalOwnedProject::projectId).toList()),
                () -> assertEquals(List.of(WithdrawalProjectOutcome.SUCCESSION, WithdrawalProjectOutcome.DELETION),
                        response.ownedProjects().stream().map(MemberResDTO.WithdrawalOwnedProject::outcome).toList()));
    }

    @Test
    @DisplayName("getWithdrawalPreview_소유 프로젝트가 없으면_빈 목록을 반환한다")
    void getWithdrawalPreview_NoOwnedProject_ReturnsEmpty() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(Role.ROLE_USER, true)));
        when(projectRepository.findOwnedProjectWithdrawalPreviewsByMemberId(MEMBER_ID)).thenReturn(List.of());

        // when
        MemberResDTO.WithdrawalPreview response = service.getWithdrawalPreview(MEMBER_ID);

        // then
        assertEquals(0, response.ownedProjects().size());
    }

    @Test
    @DisplayName("getWithdrawalPreview_회원이 없으면_MEMBER_NOT_FOUND를 던지고 프로젝트를 조회하지 않는다")
    void getWithdrawalPreview_MemberMissing_ThrowsNotFound() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        // when
        MemberException exception = assertThrows(MemberException.class, () -> service.getWithdrawalPreview(MEMBER_ID));

        // then
        assertAll(
                () -> assertEquals(MemberErrorCode.MEMBER_NOT_FOUND, exception.getCode()),
                () -> verify(projectRepository, never()).findOwnedProjectWithdrawalPreviewsByMemberId(MEMBER_ID));
    }

    @Test
    @DisplayName("getWithdrawalPreview_비활성 회원이면_MEMBER_NOT_FOUND를 던진다")
    void getWithdrawalPreview_InactiveMember_ThrowsNotFound() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(Role.ROLE_USER, false)));

        // when
        MemberException exception = assertThrows(MemberException.class, () -> service.getWithdrawalPreview(MEMBER_ID));

        // then
        assertAll(
                () -> assertEquals(MemberErrorCode.MEMBER_NOT_FOUND, exception.getCode()),
                () -> verify(projectRepository, never()).findOwnedProjectWithdrawalPreviewsByMemberId(MEMBER_ID));
    }

    @Test
    @DisplayName("getWithdrawalPreview_활성 guest_이용 종료 안내로 소유 프로젝트별 승계와 삭제를 반환한다")
    void getWithdrawalPreview_ActiveGuest_ReturnsOutcomes() {
        // given
        OwnedProjectWithdrawalPreview succession = preview(10L, "승계", true);
        OwnedProjectWithdrawalPreview deletion = preview(20L, "삭제", false);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(Role.ROLE_GUEST, true)));
        when(projectRepository.findOwnedProjectWithdrawalPreviewsByMemberId(MEMBER_ID))
                .thenReturn(List.of(succession, deletion));

        // when
        MemberResDTO.WithdrawalPreview response = service.getWithdrawalPreview(MEMBER_ID);

        // then
        assertEquals(List.of(WithdrawalProjectOutcome.SUCCESSION, WithdrawalProjectOutcome.DELETION),
                response.ownedProjects().stream().map(MemberResDTO.WithdrawalOwnedProject::outcome).toList());
    }

    private Member member(Role role, boolean active) {
        return Member.builder().email("member@test.com").password("encodedPassword").nickname("member")
                .role(role).isActive(active).build();
    }

    private OwnedProjectWithdrawalPreview preview(Long projectId, String title, boolean hasSuccessor) {
        OwnedProjectWithdrawalPreview preview = mock(OwnedProjectWithdrawalPreview.class);
        when(preview.getProjectId()).thenReturn(projectId);
        when(preview.getTitle()).thenReturn(title);
        when(preview.getHasSuccessor()).thenReturn(hasSuccessor);
        return preview;
    }
}
