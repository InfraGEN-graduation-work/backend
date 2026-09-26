package com.infragen.infragen.domain.project.controller.docs;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;

import com.infragen.infragen.domain.project.dto.request.ProjectHistoryReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectHistoryResDTO;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Project History API", description = "프로젝트 버전 관리 및 이력 API")
public interface ProjectHistoryControllerDocs {

    @Operation(summary = "프로젝트 히스토리 생성 API", description = "프로젝트 owner와 EDITOR가 활동 설명 이력을 수동 생성합니다. VIEWER는 생성할 수 없습니다. 응답에는 생성자 회원 ID가 포함되며 버전명은 v1, v2 형태로 순차적 자동 부여됩니다.")
    ApiResponse<ProjectHistoryResDTO.HistoryPreviewResDTO> createHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "프로젝트 고유 식별자", required = true, example = "1")
            @PathVariable Long projectId,
            ProjectHistoryReqDTO.CreateHistoryReqDTO request
    );

    @Operation(summary = "프로젝트 히스토리 목록 조회 API", description = "프로젝트 owner, EDITOR, VIEWER가 해당 프로젝트의 버전 히스토리 목록을 최신순으로 조회합니다. 각 이력의 생성자 회원 ID를 반환하며 과거 기록에서 알 수 없는 경우 null입니다.")
    ApiResponse<ProjectHistoryResDTO.HistoryPreviewListResDTO> getHistories(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "프로젝트 고유 식별자", required = true, example = "1")
            @PathVariable Long projectId
    );

    @Operation(summary = "프로젝트 히스토리 상세 조회 API", description = "프로젝트 owner, EDITOR, VIEWER가 특정 이력의 세부 버전 정보와 생성된 파일 목록, 생성자 회원 ID를 조회합니다. 과거 기록에서 알 수 없는 ID는 null입니다.")
    ApiResponse<ProjectHistoryResDTO.HistoryDetailResDTO> getHistoryDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "프로젝트 고유 식별자", required = true, example = "1")
            @PathVariable Long projectId,
            @Parameter(description = "프로젝트 히스토리 고유 식별자", required = true, example = "1")
            @PathVariable Long historyId
    );
}
