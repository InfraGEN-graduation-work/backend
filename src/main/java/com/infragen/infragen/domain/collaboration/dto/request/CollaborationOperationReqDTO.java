package com.infragen.infragen.domain.collaboration.dto.request;

import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Map;

public final class CollaborationOperationReqDTO {
    private CollaborationOperationReqDTO() {
    }

    public record Operation(
            @NotBlank String operationId,
            @NotBlank String clientId,
            @NotNull @PositiveOrZero Long baseVersion,
            @NotNull CollaborationOperationType type,
            @NotBlank String nodeId,
            @NotNull Map<String, Object> payload
    ) {
    }
}
