package com.infragen.infragen.domain.project.controller.docs;

import com.infragen.infragen.domain.project.dto.request.ProjectCollaboratorReqDTO;
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

    @Operation(summary = "프로젝트 collaborator 등록")
    ApiResponse<ProjectCollaboratorResDTO.Detail> addCollaborator(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId,
            ProjectCollaboratorReqDTO.Add request
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
