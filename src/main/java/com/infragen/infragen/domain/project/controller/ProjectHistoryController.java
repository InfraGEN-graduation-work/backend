package com.infragen.infragen.domain.project.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.infragen.infragen.domain.project.controller.docs.ProjectHistoryControllerDocs;
import com.infragen.infragen.domain.project.dto.request.ProjectHistoryReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectHistoryResDTO;
import com.infragen.infragen.domain.project.exception.code.success.ProjectHistorySuccessCode;
import com.infragen.infragen.domain.project.service.command.ProjectHistoryCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectHistoryQueryService;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/histories")
@RequiredArgsConstructor
public class ProjectHistoryController implements ProjectHistoryControllerDocs {
    private final ProjectHistoryCommandService projectHistoryCommandService;
    private final ProjectHistoryQueryService projectHistoryQueryService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectHistoryResDTO.HistoryPreviewResDTO> createHistory(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long projectId,
        @RequestBody @Valid ProjectHistoryReqDTO.CreateHistoryReqDTO request
    ) {
        var result = projectHistoryCommandService.createHistory(
            projectId,
            request,
            userDetails.getMemberId()
        );

        return ApiResponse.onSuccess(
            ProjectHistorySuccessCode.HISTORY_CREATE_SUCCESS,
            result
        );
    }

    @Override
    @GetMapping
    public ApiResponse<ProjectHistoryResDTO.HistoryPreviewListResDTO> getHistories(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long projectId
    ) {
        var result = projectHistoryQueryService.getHistories(
            projectId,
            userDetails.getMemberId()
        );

        return ApiResponse.onSuccess(
            ProjectHistorySuccessCode.HISTORY_GET_SUCCESS,
            result
        );
    }

    @Override
    @GetMapping("/{historyId}")
    public ApiResponse<ProjectHistoryResDTO.HistoryDetailResDTO> getHistoryDetail(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long projectId,
        @PathVariable Long historyId
    ) {
        var result = projectHistoryQueryService.getHistoryDetail(
            projectId,
            historyId,
            userDetails.getMemberId()
        );

        return ApiResponse.onSuccess(
            ProjectHistorySuccessCode.HISTORY_DETAIL_GET_SUCCESS,
            result
        );
    }
}
