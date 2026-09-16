package com.infragen.infragen.domain.project.controller.docs;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import com.infragen.infragen.domain.project.dto.request.ProjectReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Project API", description = "프로젝트 관련 API")
public interface ProjectControllerDocs {
    @Operation(summary = "신규 프로젝트 생성 API", description = "새로운 인프라 설계 프로젝트를 생성합니다. 초기 상태는 DRAFT(초안)입니다.")
    ApiResponse<ProjectResDTO.CreateProjectResDTO> createProject(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            ProjectReqDTO.CreateProjectReqDTO request
    );

    @Operation(summary = "프로젝트 목록 조회 API", description = "인증된 사용자의 소유·참여 프로젝트를 생성일 최신순으로 조회합니다. accessRole은 OWNER, EDITOR, VIEWER이며 참여 프로젝트 진입에는 /{projectId}/collaboration?afterVersion=0을 사용합니다.")
    ApiResponse<ProjectResDTO.ProjectPreviewListResDTO> getProjects(
            @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "프로젝트 삭제 API", description = "특정 프로젝트와 그에 포함된 모든 캔버스 데이터를 완전히 삭제(Hard Delete)합니다.")
    ApiResponse<Void> deleteProject(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId
    );

    @Operation(summary = "프로젝트 상세 캔버스 조회 API", description = "특정 프로젝트의 식별자(ID)를 바탕으로 캔버스 내 노드 및 엣지 세부 매핑 정보를 복원 조회합니다.")
    ApiResponse<ProjectResDTO.ProjectDetailResDTO> getProjectDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId
    );

    @Operation(summary = "프로젝트 이름·설명 수정 API", description = "OWNER만 호출할 수 있습니다. title(공백 제외 필수, 최대 100자)과 baseVersion(0 이상)은 필수이며 description 생략/null은 유지, 빈 문자열은 비우기입니다. 현재 version 불일치는 COLLAB409_2, 소유 프로젝트가 아니면 PROJECT404_1입니다. node·edge를 보존하고 commit 후 room resync를 전송합니다. 성공 코드는 PROJECT200_8입니다.")
    ApiResponse<ProjectResDTO.ProjectPreviewResDTO> updateMetadata(
            @PathVariable Long projectId,
            ProjectReqDTO.UpdateMetadata request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "프로젝트 캔버스 저장/수정 API", description = "특정 프로젝트의 식별자(ID), client가 알고 있는 baseVersion, 수정된 캔버스 데이터(노드/엣지 전체 목록)를 받아 version을 확인한 뒤 저장합니다. 오래된 baseVersion은 거부됩니다.")
    ApiResponse<ProjectResDTO.ProjectDetailResDTO> updateProject(
            @PathVariable Long projectId,
            ProjectReqDTO.UpdateProjectReqDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    );
}
