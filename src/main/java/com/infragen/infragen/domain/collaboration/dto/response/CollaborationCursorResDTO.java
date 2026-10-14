package com.infragen.infragen.domain.collaboration.dto.response;

import lombok.Builder;

public final class CollaborationCursorResDTO {
    private CollaborationCursorResDTO() {
    }

    @Builder
    public record BroadcastCursorResDTO(
            Long actorMemberId,
            String cursorId,
            Boolean visible,
            Double x,
            Double y
    ) {
    }
}
