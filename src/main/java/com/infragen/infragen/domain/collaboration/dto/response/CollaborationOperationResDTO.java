package com.infragen.infragen.domain.collaboration.dto.response;

import java.util.Map;

import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;

import lombok.Builder;

public final class CollaborationOperationResDTO {
    private CollaborationOperationResDTO() {
    }

    @Builder
    public record BroadcastOperationResDTO(
            String operationId,
            String clientId,
            Long serverVersion,
            Long actorMemberId,
            CollaborationOperationType type,
            String nodeId,
            Map<String, Object> payload
    ) {
    }

    @Builder
    public record OperationErrorResDTO(
            String code,
            String message
    ) {
    }
}
