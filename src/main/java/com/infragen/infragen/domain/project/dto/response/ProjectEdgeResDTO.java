package com.infragen.infragen.domain.project.dto.response;

import lombok.Builder;

public class ProjectEdgeResDTO {
    @Builder
    public record EdgeInfoResDTO(
        Long id,
        String sourceNodeId,
        String targetNodeId
    ) {}
}
