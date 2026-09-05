package com.infragen.infragen.domain.collaboration.dto.response;

import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import lombok.Builder;

import java.util.List;

public final class CollaborationSnapshotResDTO {
    private CollaborationSnapshotResDTO() {
    }

    @Builder
    public record SnapshotResDTO(
            ProjectResDTO.ProjectDetailResDTO project,
            Long serverVersion,
            List<CollaborationOperationResDTO.BroadcastOperationResDTO> operations
    ) {
    }
}
