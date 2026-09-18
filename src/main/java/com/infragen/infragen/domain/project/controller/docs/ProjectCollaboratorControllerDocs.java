package com.infragen.infragen.domain.project.controller.docs;

import com.infragen.infragen.domain.project.dto.request.ProjectCollaboratorReqDTO;
import com.infragen.infragen.domain.project.dto.request.ProjectCollaboratorInvitationReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorResDTO;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Project Collaborator API", description = "프로젝트 collaborator 관리 API")
public interface ProjectCollaboratorControllerDocs {
    @Operation(summary = "프로젝트 collaborator 목록 조회")
    ApiResponse<ProjectCollaboratorResDTO.ListResult> getCollaborators(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId
    );

    @Operation(
            summary = "프로젝트 발신 초대 목록 조회",
            description = "프로젝트 협업 관리 화면에서 owner가 자신이 보낸 초대의 처리 상태를 확인할 때 사용합니다. 로그인한 owner가 projectId를 지정해 GET /api/v1/projects/{projectId}/collaborators/invitations를 호출하면, 대상 회원 표시명·역할·상태(PENDING, ACCEPTED, DECLINED, EXPIRED)와 생성·만료·응답 시각을 확인할 수 있습니다."
    )
    ApiResponse<ProjectCollaboratorInvitationResDTO.SentList> getSentInvitations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId
    );

    @Operation(
            summary = "프로젝트 collaborator 초대 발신",
            description = "프로젝트 owner가 협업할 회원의 초대코드와 역할을 보내 특정 프로젝트 초대를 생성할 때 사용합니다. POST /api/v1/projects/{projectId}/collaborators/invitations 요청에 inviteeCode와 EDITOR 또는 VIEWER 역할을 담으면 초대가 PENDING으로 저장됩니다. 초대받은 회원이 수락하기 전까지 collaborator membership은 생성되지 않습니다."
    )
    ApiResponse<Void> inviteCollaborator(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId,
            ProjectCollaboratorInvitationReqDTO.Create request
    );

    @Operation(
            summary = "숫자 memberId 직접 collaborator 등록 (사용 중단)",
            description = "신규 collaborator는 POST /api/v1/projects/{projectId}/collaborators/invitations에서 초대코드로 초대해야 합니다. 이 기존 경로는 요청한 owner에게 PROJECT403_1을 반환하며 collaborator를 생성하지 않습니다."
    )
    void rejectMemberIdAddition(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId
    );

    @Operation(summary = "프로젝트 collaborator 역할 변경")
    ApiResponse<Void> changeCollaboratorRole(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId,
            @PathVariable Long memberId,
            ProjectCollaboratorReqDTO.ChangeRole request
    );

    @Operation(summary = "프로젝트 collaborator 삭제")
    ApiResponse<Void> deleteCollaborator(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId,
            @PathVariable Long memberId
    );
}
