package com.infragen.infragen.domain.project.dto.request;

import lombok.Builder;

public class ProjectHistoryReqDTO {
    @Builder
    public record CreateHistoryReqDTO(
        String description
    ) {
    }
}
