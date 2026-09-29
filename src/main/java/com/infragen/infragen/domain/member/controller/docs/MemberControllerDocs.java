package com.infragen.infragen.domain.member.controller.docs;

import com.infragen.infragen.domain.member.dto.request.MemberReqDTO;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Member API", description = "회원 관련 API")
public interface MemberControllerDocs {
    @Operation(summary = "내 회원 정보 조회 API", description = "로그인한 회원의 정보를 조회합니다.")
    ApiResponse<MemberResDTO.MemberResultDTO> getMe(
            @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(
            summary = "내 초대코드 발급 또는 조회",
            description = "인증된 회원의 초대코드를 보장하고 본인 코드만 반환합니다."
    )
    ApiResponse<MemberResDTO.InvitationCode> ensureInvitationCode(
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

    @Operation(
            summary = "회원 탈퇴 전 프로젝트 처리 안내 조회 API",
            description = """
                    탈퇴하면 내가 소유한 프로젝트가 어떻게 처리되는지 project ID 순으로 알려줍니다.
                    outcome이 SUCCESSION이면 활성 참여자 중 한 명이 새 owner가 되어 프로젝트가 유지되고,
                    DELETION이면 승계할 활성 참여자가 없어 프로젝트와 종속 데이터가 삭제됩니다.
                    소유한 프로젝트가 없으면 ownedProjects는 빈 목록입니다.
                    조회 시점의 참고용 안내이며 조회만 할 뿐 탈퇴는 진행하지 않습니다.
                    일반 회원만 조회할 수 있고 guest는 GUEST_ACTION_NOT_ALLOWED로 거부됩니다.
                    """
    )
    ApiResponse<MemberResDTO.WithdrawalPreview> getWithdrawalPreview(
            @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(
            summary = "회원 탈퇴 API",
            description = """
                    로그인한 일반 회원을 Soft Delete 방식으로 탈퇴 처리합니다.
                    소유한 프로젝트는 활성 참여자가 있으면 그 중 한 명에게 승계되고, 없으면 종속 데이터와 함께 삭제됩니다.
                    관련된 대기 초대는 취소되고 다른 프로젝트의 참여 기록은 제거되며, 처리가 끝난 초대 이력은 보존됩니다.
                    모든 처리는 하나의 트랜잭션이라 중간에 실패하면 전체가 되돌려집니다.
                    삭제되는 프로젝트가 있으면 탈퇴 전 안내 조회 API에서 DELETION으로 표시된 프로젝트 ID를
                    confirmedDeletionProjectIds에 담아 보내야 합니다. 예: ?confirmedDeletionProjectIds=2,5
                    삭제되는 프로젝트가 없으면 생략할 수 있습니다.
                    확인하지 않은 삭제 프로젝트가 있으면 아무것도 변경하지 않고 MEMBER409_3을 반환하므로,
                    안내를 다시 조회해 재확인해야 합니다. 처리 중 프로젝트 관계가 바뀌면 COMMON409_2를 반환하며 잠시 후 다시 시도할 수 있습니다.
                    guest는 GUEST_ACTION_NOT_ALLOWED로 거부됩니다.
                    """
    )
    ApiResponse<Void> withdrawMember(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "삭제된다고 확인한 프로젝트 ID 목록(쉼표 구분). 삭제되는 프로젝트가 없으면 생략")
            Set<Long> confirmedDeletionProjectIds,
            HttpServletResponse response
    );
}
