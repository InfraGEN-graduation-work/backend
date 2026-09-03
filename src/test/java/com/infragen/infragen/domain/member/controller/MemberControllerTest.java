package com.infragen.infragen.domain.member.controller;

import com.infragen.infragen.domain.auth.service.AuthService;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
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
        var inOrder = inOrder(authService, refreshTokenCookieWriter);
        inOrder.verify(authService).withdrawMember(1L);
        inOrder.verify(refreshTokenCookieWriter).clear(response);
    }
}
