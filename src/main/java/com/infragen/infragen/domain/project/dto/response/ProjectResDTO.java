package com.infragen.infragen.domain.project.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;

public class ProjectResDTO {
    @Builder
    public record CreateProjectResDTO(
        Long projectId,
        LocalDateTime createdAt
    ){
    }

    @Builder
    public record ProjectPreviewResDTO(
        Long projectId,
        String title,
        String description,
        String status,
        LocalDateTime createdAt
    ) {
    }

    @Builder
    public record ProjectPreviewListResDTO(
        List<ProjectPreviewResDTO> projectList
    ) {
    }

    // 상세 조회 응답 DTO
    @Builder
    public record ProjectDetailResDTO(
        Long projectId,
        String title,
        String description,
        String status,
        List<ProjectNodeResDTO.NodeInfoResDTO> nodes,
        List<ProjectEdgeResDTO.EdgeInfoResDTO> edges
    ) {}

}
