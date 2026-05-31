package com.infragen.infragen.domain.project.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

public class ProjectReqDTO {
    @Builder
    public record CreateProjectReqDTO(
        @NotBlank(message = "프로젝트 이름은 필수 입력 항목입니다.")
        String title,
        String description
    ) {
    }

    // 수정 및 저장 요청 DTO
    public record UpdateProjectReqDTO(
        @NotBlank(message = "프로젝트 이름은 필수 입력 항목입니다.")
        String title,
        String description,
        // 리스트 내부의 객체 유효성 검사 전파
        @Valid 
        @NotNull
        List<ProjectNodeReqDTO.NodeInfoReqDTO> nodes,
        @Valid
        @NotNull
        List<ProjectEdgeReqDTO.EdgeInfoReqDTO> edges
    ) {
    }
}
