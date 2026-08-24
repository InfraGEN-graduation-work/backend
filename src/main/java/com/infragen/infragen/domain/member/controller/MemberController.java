package com.infragen.infragen.domain.member.controller;

import com.infragen.infragen.domain.auth.service.AuthService;
import com.infragen.infragen.domain.member.controller.docs.MemberControllerDocs;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.exception.code.success.MemberSuccessCode;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;
import com.infragen.infragen.global.auth.RefreshTokenCookieWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController implements MemberControllerDocs {
    private final MemberQueryService memberQueryService;
    private final AuthService authService;
    private final RefreshTokenCookieWriter refreshTokenCookieWriter;

    @Override
    @GetMapping("/me")
    public ApiResponse<MemberResDTO.MemberResultDTO> getMe(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        var result = memberQueryService.getMe(userDetails.getMemberId());
        return ApiResponse.onSuccess(MemberSuccessCode.MEMBER_GET_SUCCESS, result);
    }

    @Override
    @PostMapping("/logout")
    public ApiResponse<String> logout(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader("Authorization") String authorization,
            HttpServletResponse response
    ) {
        authService.logout(authorization);
        refreshTokenCookieWriter.clear(response);
        return ApiResponse.onSuccess(MemberSuccessCode.LOGOUT_SUCCESS, "로그아웃을 성공했습니다.");
    }
}
