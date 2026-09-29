package com.infragen.infragen.domain.member.controller;

import com.infragen.infragen.domain.auth.service.AuthService;
import com.infragen.infragen.domain.member.controller.docs.MemberControllerDocs;
import com.infragen.infragen.domain.member.dto.request.MemberReqDTO;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.exception.code.success.MemberSuccessCode;
import com.infragen.infragen.domain.member.service.command.MemberCommandService;
import com.infragen.infragen.domain.member.service.command.MemberWithdrawalCommandService;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.member.service.query.MemberWithdrawalQueryService;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;
import com.infragen.infragen.global.auth.RefreshTokenCookieWriter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController implements MemberControllerDocs {
    private final MemberQueryService memberQueryService;
    private final MemberCommandService memberCommandService;
    private final MemberWithdrawalQueryService memberWithdrawalQueryService;
    private final MemberWithdrawalCommandService memberWithdrawalCommandService;
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

    /** 본인 초대코드를 보장하고 기존 유효 코드는 그대로 반환한다. */
    @Override
    @PostMapping("/me/invitation-code")
    public ApiResponse<MemberResDTO.InvitationCode> ensureInvitationCode(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.onSuccess(
                MemberSuccessCode.MEMBER_INVITATION_CODE_ENSURE_SUCCESS,
                memberCommandService.ensureInvitationCode(userDetails.getMemberId())
        );
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

    @Override
    @PatchMapping("/me")
    public ApiResponse<MemberResDTO.MemberResultDTO> updateMember(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid MemberReqDTO.UpdateMember request
    ) {
        var result = memberCommandService.updateMember(userDetails.getMemberId(), request);
        return ApiResponse.onSuccess(MemberSuccessCode.MEMBER_UPDATE_SUCCESS, result);
    }

    /** 탈퇴 전에 소유 프로젝트가 승계될지 삭제될지 조회한다. 조회만 하며 탈퇴는 진행하지 않는다. */
    @Override
    @GetMapping("/me/withdrawal-preview")
    public ApiResponse<MemberResDTO.WithdrawalPreview> getWithdrawalPreview(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        var result = memberWithdrawalQueryService.getWithdrawalPreview(userDetails.getMemberId());
        return ApiResponse.onSuccess(MemberSuccessCode.MEMBER_WITHDRAWAL_PREVIEW_SUCCESS, result);
    }

    /**
     * 회원을 탈퇴 처리하고 소유 프로젝트를 승계하거나 삭제한다. 탈퇴 전 안내에서 확인한 삭제 프로젝트 ID를 받아,
     * 실제 삭제 대상이 그 안에 없으면 아무것도 변경하지 않고 거부한다. 삭제되는 프로젝트가 없으면 생략할 수 있다.
     */
    @Override
    @DeleteMapping("/me")
    public ApiResponse<Void> withdrawMember(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Set<Long> confirmedDeletionProjectIds,
            HttpServletResponse response
    ) {
        memberWithdrawalCommandService.withdraw(
                userDetails.getMemberId(),
                confirmedDeletionProjectIds == null ? Set.of() : confirmedDeletionProjectIds
        );
        refreshTokenCookieWriter.clear(response);
        return ApiResponse.onSuccess(MemberSuccessCode.MEMBER_WITHDRAW_SUCCESS, null);
    }
}
