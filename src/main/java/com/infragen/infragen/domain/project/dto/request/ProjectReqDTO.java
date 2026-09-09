package com.infragen.infragen.domain.project.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

public class ProjectReqDTO {
    @Builder
    public record CreateProjectReqDTO(
        @NotBlank(message = "프로젝트 이름은 필수 입력 항목입니다.")
        String title,
        String description
    ) {
    }

    /**
     * 프로젝트 수정 요청 DTO
     */
    public record UpdateProjectReqDTO(
        @NotBlank(message = "프로젝트 이름은 필수 입력 항목입니다.")
        String title,
        String description,
        // 리스트 내부의 객체 유효성 검사를 전파한다.
        @Valid 
        @NotNull
        List<ProjectNodeReqDTO.NodeInfoReqDTO> nodes,
        @Valid
        @NotNull
        List<ProjectEdgeReqDTO.EdgeInfoReqDTO> edges,
        @NotNull
        @PositiveOrZero
        Long baseVersion
    ) {
    }
}
