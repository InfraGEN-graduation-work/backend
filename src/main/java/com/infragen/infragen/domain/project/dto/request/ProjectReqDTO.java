package com.infragen.infragen.domain.project.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;

public final class ProjectReqDTO {
    private ProjectReqDTO() {
    }

    /** 설명을 생략하거나 null로 보내면 기존 값을 유지하며 빈 문자열로 비울 수 있다. */
    public record UpdateMetadata(
        @NotBlank(message = "프로젝트 이름은 필수 입력 항목입니다.")
        @Size(max = 100, message = "프로젝트 이름은 100자 이하여야 합니다.")
        String title,
        String description,
        @NotNull(message = "기준 버전은 필수 입력 항목입니다.")
        @PositiveOrZero(message = "기준 버전은 0 이상이어야 합니다.")
        Long baseVersion
    ) {
    }

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
