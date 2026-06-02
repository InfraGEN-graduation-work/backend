package com.infragen.infragen.domain.project.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.infragen.infragen.domain.project.controller.docs.ProjectControllerDocs;
import com.infragen.infragen.domain.project.dto.request.ProjectReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.exception.code.success.ProjectSuccessCode;
import com.infragen.infragen.domain.project.service.command.ProjectCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController implements ProjectControllerDocs {
    private final ProjectCommandService projectCommandService;
    private final ProjectQueryService projectQueryService;

    @Override
    @PostMapping
    @ResponseStatus(code = HttpStatus.CREATED)
    public ApiResponse<ProjectResDTO.CreateProjectResDTO> createProject(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @RequestBody @Valid ProjectReqDTO.CreateProjectReqDTO request
    ) {
        var result = projectCommandService.createProject(
            request,
            userDetails.getMemberId() 
        );

        return ApiResponse.onSuccess(
            ProjectSuccessCode.PROJECT_CREATE_SUCCESS,
            result
        );
    }

    @Override
    @GetMapping
    public ApiResponse<ProjectResDTO.ProjectPreviewListResDTO> getProjects(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        var result = projectQueryService.getProjects(userDetails.getMemberId());
        return ApiResponse.onSuccess(
            ProjectSuccessCode.PROJECT_GET_SUCCESS,
            result
        );
    }

    @Override
    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> deleteProject(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long projectId
    ) {
        projectCommandService.deleteProject(projectId, userDetails.getMemberId());
        return ApiResponse.onSuccess(
            ProjectSuccessCode.PROJECT_DELETE_SUCCESS,
            null
        );
    }

    @Override
    @GetMapping("/{projectId}")
    public ApiResponse<ProjectResDTO.ProjectDetailResDTO> getProjectDetail(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long projectId
    ) {
        var result = projectQueryService.getProjectDetail(projectId, userDetails.getMemberId());
        return ApiResponse.onSuccess(
            ProjectSuccessCode.PROJECT_CANVAS_GET_SUCCESS,
            result
        );
    }

    @Override
    @PutMapping("/{projectId}")
    public ApiResponse<ProjectResDTO.ProjectDetailResDTO> updateProject(
        @PathVariable Long projectId,
        @RequestBody @Valid ProjectReqDTO.UpdateProjectReqDTO request,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        var result = projectCommandService.updateProject(projectId, request, userDetails.getMemberId());
        return ApiResponse.onSuccess(
            ProjectSuccessCode.PROJECT_CANVAS_SAVE_SUCCESS,
            result
        );
    }
}
