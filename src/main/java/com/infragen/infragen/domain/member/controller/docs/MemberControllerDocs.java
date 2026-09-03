package com.infragen.infragen.domain.member.controller.docs;

import com.infragen.infragen.domain.member.dto.request.MemberReqDTO;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Member API", description = "회원 관련 API")
public interface MemberControllerDocs {
    @Operation(summary = "내 회원 정보 조회 API", description = "로그인한 회원의 정보를 조회합니다.")
    ApiResponse<MemberResDTO.MemberResultDTO> getMe(
            @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "로그아웃 API", description = "액세스 토큰을 블랙리스트에 등록하고 리프레시 토큰을 삭제합니다.")
    ApiResponse<String> logout(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            String authorization,
            HttpServletResponse response
    );

    @Operation(summary = "회원 정보 수정 API", description = "일반 회원은 닉네임과 비밀번호를, 소셜 회원은 닉네임을 수정합니다.")
    ApiResponse<MemberResDTO.MemberResultDTO> updateMember(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid MemberReqDTO.UpdateMember request
    );

    @Operation(summary = "회원 탈퇴 API", description = "로그인한 회원을 Soft Delete 방식으로 탈퇴 처리합니다.")
    ApiResponse<Void> withdrawMember(
            @AuthenticationPrincipal CustomUserDetails userDetails
    );
}
