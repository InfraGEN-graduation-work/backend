package com.infragen.infragen.domain.project.dto.request;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ProjectNodeReqDTO {
    // 캔버스 저장 및 수정 시 사용되는 노드 개별 DTO
    public record NodeInfoReqDTO(
        @NotBlank(message = "노드 이름은 필수입니다.")
        String nodeName,
        
        @NotBlank(message = "컴포넌트 타입은 필수입니다.")
        String componentType,
        
        @NotNull(message = "X 좌표는 필수입니다.")
        BigDecimal positionX,
        
        @NotNull(message = "Y 좌표는 필수입니다.")
        BigDecimal positionY,
        
        Map<String, Object> properties
    ) {
    }
}
