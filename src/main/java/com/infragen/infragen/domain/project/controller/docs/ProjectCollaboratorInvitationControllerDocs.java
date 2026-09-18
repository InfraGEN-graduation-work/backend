package com.infragen.infragen.domain.project.controller.docs;

import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Project Collaborator Invitation API", description = "프로젝트 초대 조회 및 응답 API")
public interface ProjectCollaboratorInvitationControllerDocs {
    @Operation(
            summary = "받은 프로젝트 초대 목록 조회",
            description = "로그인한 회원이 자신의 받은 초대함을 확인할 때 사용합니다. GET /api/v1/project-collaborator-invitations/received를 호출하면 JWT 회원에게 온 초대만 반환합니다. status를 PENDING, ACCEPTED, DECLINED 또는 EXPIRED로 지정하면 해당 상태만 조회하고, 생략하면 모든 상태의 초대를 조회합니다. 목록에는 프로젝트명, 초대한 사람, 요청 역할, 상태, 생성 시각과 만료 시각이 포함됩니다."
    )
    ApiResponse<ProjectCollaboratorInvitationResDTO.ReceivedList> getReceivedInvitations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회할 초대 상태. 생략하면 전체 상태를 반환합니다.", example = "PENDING")
            @RequestParam(name = "status", required = false)
            ProjectCollaboratorInvitationResDTO.InvitationStatus statusFilter
    );

    @Operation(
            summary = "프로젝트 초대 수락",
            description = "로그인한 초대 대상 회원이 받은 초대를 수락할 때 사용합니다. POST /api/v1/project-collaborator-invitations/{invitationId}/accept를 호출하면 서버가 JWT 회원 ID와 초대 대상을 확인하고 collaborator membership을 생성합니다. 요청 body에 회원 ID나 초대코드는 넣지 않습니다."
    )
    ApiResponse<Void> acceptInvitation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "수락할 초대 ID") @PathVariable Long invitationId
    );

    @Operation(
            summary = "프로젝트 초대 거절",
            description = "로그인한 초대 대상 회원이 받은 초대를 거절할 때 사용합니다. POST /api/v1/project-collaborator-invitations/{invitationId}/decline를 호출하면 서버가 JWT 회원 ID와 초대 대상을 확인하고 invitation 상태만 DECLINED로 변경합니다. 요청 body에 회원 ID나 초대코드는 넣지 않습니다."
    )
    ApiResponse<Void> declineInvitation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "거절할 초대 ID") @PathVariable Long invitationId
    );
}
