package com.infragen.infragen.domain.project.dto.response;

import java.math.BigDecimal;
import java.util.Map;

import lombok.Builder;

public class ProjectNodeResDTO {
    @Builder
    public record NodeInfoResDTO(
        Long id,
        String nodeName,
        String componentType,
        BigDecimal positionX,
        BigDecimal positionY,
        Map<String, Object> properties
    ) {}
}
