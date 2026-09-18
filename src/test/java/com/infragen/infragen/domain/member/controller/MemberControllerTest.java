package com.infragen.infragen.domain.member.controller;

import com.infragen.infragen.domain.auth.service.AuthService;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.exception.code.success.MemberSuccessCode;
import com.infragen.infragen.domain.member.service.command.MemberCommandService;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.global.auth.CustomUserDetails;
import com.infragen.infragen.global.auth.RefreshTokenCookieWriter;
import jakarta.servlet.http.HttpServletResponse;
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
    void withdrawMember_Success_ClearsRefreshTokenCookie() {
        // given
        CustomUserDetails userDetails = new CustomUserDetails(
                MemberResDTO.MemberResultDTO.builder()
                        .id(1L)
                        .isActive(true)
                        .build()
        );

        // when
        memberController.withdrawMember(userDetails, response);

        // then
        var inOrder = inOrder(memberCommandService, refreshTokenCookieWriter);
        inOrder.verify(memberCommandService).withdrawMember(1L);
        inOrder.verify(refreshTokenCookieWriter).clear(response);
    }
}
