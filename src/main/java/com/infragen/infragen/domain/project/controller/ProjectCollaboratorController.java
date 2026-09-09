package com.infragen.infragen.domain.project.controller;

import com.infragen.infragen.domain.project.controller.docs.ProjectCollaboratorControllerDocs;
import com.infragen.infragen.domain.project.dto.request.ProjectCollaboratorReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorResDTO;
import com.infragen.infragen.domain.project.exception.code.success.ProjectSuccessCode;
import com.infragen.infragen.domain.project.service.command.ProjectCollaboratorCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectCollaboratorQueryService;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/collaborators")
public class ProjectCollaboratorController implements ProjectCollaboratorControllerDocs {
    private final ProjectCollaboratorQueryService collaboratorQueryService;
    private final ProjectCollaboratorCommandService collaboratorCommandService;

    @Override
    @GetMapping
    public ApiResponse<ProjectCollaboratorResDTO.ListResult> getCollaborators(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId
    ) {
        return ApiResponse.onSuccess(
                ProjectSuccessCode.PROJECT_COLLABORATOR_GET_SUCCESS,
                collaboratorQueryService.getAll(projectId, userDetails.getMemberId())
        );
    }

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectCollaboratorResDTO.Detail> addCollaborator(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectCollaboratorReqDTO.Add request
    ) {
        return ApiResponse.onSuccess(
                ProjectSuccessCode.PROJECT_COLLABORATOR_ADD_SUCCESS,
                collaboratorCommandService.add(projectId, userDetails.getMemberId(), request)
        );
    }

    @Override
    @PatchMapping("/{memberId}")
    public ApiResponse<Void> changeCollaboratorRole(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId,
            @PathVariable Long memberId,
            @Valid @RequestBody ProjectCollaboratorReqDTO.ChangeRole request
    ) {
        collaboratorCommandService.changeRole(
                projectId,
                userDetails.getMemberId(),
                memberId,
                request
        );
        return ApiResponse.onSuccess(ProjectSuccessCode.PROJECT_COLLABORATOR_ROLE_UPDATE_SUCCESS, null);
    }

    @Override
    @DeleteMapping("/{memberId}")
    public ApiResponse<Void> deleteCollaborator(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId,
            @PathVariable Long memberId
    ) {
        collaboratorCommandService.delete(projectId, userDetails.getMemberId(), memberId);
        return ApiResponse.onSuccess(ProjectSuccessCode.PROJECT_COLLABORATOR_DELETE_SUCCESS, null);
    }
}
