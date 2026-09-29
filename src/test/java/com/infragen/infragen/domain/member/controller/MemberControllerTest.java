package com.infragen.infragen.domain.member.controller;

import com.infragen.infragen.domain.auth.service.AuthService;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.enums.WithdrawalProjectOutcome;
import com.infragen.infragen.domain.member.exception.code.success.MemberSuccessCode;
import com.infragen.infragen.domain.member.service.command.MemberCommandService;
import com.infragen.infragen.domain.member.service.command.MemberWithdrawalCommandService;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.member.service.query.MemberWithdrawalQueryService;
import com.infragen.infragen.global.auth.CustomUserDetails;
import com.infragen.infragen.global.auth.RefreshTokenCookieWriter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    @Mock
    private MemberQueryService memberQueryService;

    @Mock
    private MemberCommandService memberCommandService;

    @Mock
    private MemberWithdrawalQueryService memberWithdrawalQueryService;

    @Mock
    private MemberWithdrawalCommandService memberWithdrawalCommandService;

    @Mock
    private AuthService authService;

    @Mock
    private RefreshTokenCookieWriter refreshTokenCookieWriter;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private MemberController memberController;

    @Test
    void ensureInvitationCode_ReturnsOwnCode() {
        // given
        CustomUserDetails userDetails = new CustomUserDetails(
                MemberResDTO.MemberResultDTO.builder()
                        .id(3L)
                        .role(Role.ROLE_USER)
                        .isActive(true)
                        .build()
        );
        MemberResDTO.InvitationCode expected = MemberResDTO.InvitationCode.builder()
                .inviteCode("A1B2C3D4")
                .build();
        when(memberCommandService.ensureInvitationCode(3L)).thenReturn(expected);

        // when
        var response = memberController.ensureInvitationCode(userDetails);

        // then
        assertEquals(MemberSuccessCode.MEMBER_INVITATION_CODE_ENSURE_SUCCESS.getCode(), response.getCode());
        assertEquals(expected, response.getResult());
        verify(memberCommandService).ensureInvitationCode(3L);
    }

    @Test
    void withdrawMember_Success_WithdrawsThenClearsRefreshTokenCookie() {
        // given
        CustomUserDetails userDetails = new CustomUserDetails(
                MemberResDTO.MemberResultDTO.builder()
                        .id(1L)
                        .isActive(true)
                        .build()
        );

        // when
        memberController.withdrawMember(userDetails, Set.of(2L, 5L), response);

        // then
        var inOrder = inOrder(memberWithdrawalCommandService, refreshTokenCookieWriter);
        inOrder.verify(memberWithdrawalCommandService).withdraw(1L, Set.of(2L, 5L));
        inOrder.verify(refreshTokenCookieWriter).clear(response);
    }

    @Test
    void withdrawMember_NoConfirmation_PassesEmptySet() {
        // given
        CustomUserDetails userDetails = new CustomUserDetails(
                MemberResDTO.MemberResultDTO.builder()
                        .id(1L)
                        .isActive(true)
                        .build()
        );

        // when
        memberController.withdrawMember(userDetails, null, response);

        // then
        verify(memberWithdrawalCommandService).withdraw(1L, Set.of());
    }

    @Test
    void getWithdrawalPreview_Success_DelegatesWithAuthenticatedMemberId() {
        // given
        CustomUserDetails userDetails = new CustomUserDetails(
                MemberResDTO.MemberResultDTO.builder()
                        .id(5L)
                        .role(Role.ROLE_USER)
                        .isActive(true)
                        .build()
        );
        MemberResDTO.WithdrawalPreview expected = MemberResDTO.WithdrawalPreview.builder()
                .ownedProjects(List.of(MemberResDTO.WithdrawalOwnedProject.builder()
                        .projectId(10L)
                        .title("삭제 프로젝트")
                        .outcome(WithdrawalProjectOutcome.DELETION)
                        .build()))
                .build();
        when(memberWithdrawalQueryService.getWithdrawalPreview(5L)).thenReturn(expected);

        // when
        var response = memberController.getWithdrawalPreview(userDetails);

        // then
        assertEquals(MemberSuccessCode.MEMBER_WITHDRAWAL_PREVIEW_SUCCESS.getCode(), response.getCode());
        assertEquals(expected, response.getResult());
        verify(memberWithdrawalQueryService).getWithdrawalPreview(5L);
    }
}
