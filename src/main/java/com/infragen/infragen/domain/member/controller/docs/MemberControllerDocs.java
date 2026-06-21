package com.infragen.infragen.domain.member.controller.docs;

import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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
}
