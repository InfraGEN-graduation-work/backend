package com.infragen.infragen.domain.project.dto.request;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ProjectNodeReqDTO {
    // 캔버스 저장 및 수정 시 사용되는 노드 개별 DTO
    // 좌표 범위는 ProjectNode.positionX/Y 컬럼(DECIMAL(10,3))의 정수부 7자리 한도다. 소수 자리는 제한하지 않는다.
    public record NodeInfoReqDTO(
        @NotBlank(message = "노드 ID는 필수입니다.")
        String nodeId,

        @NotBlank(message = "노드 이름은 필수입니다.")
        String nodeName,
        
        @NotBlank(message = "컴포넌트 타입은 필수입니다.")
        String componentType,
        
        @NotNull(message = "X 좌표는 필수입니다.")
        @DecimalMin(value = "-9999999.999", message = "X 좌표는 -9999999.999 이상이어야 합니다.")
        @DecimalMax(value = "9999999.999", message = "X 좌표는 9999999.999 이하여야 합니다.")
        BigDecimal positionX,
        
        @NotNull(message = "Y 좌표는 필수입니다.")
        @DecimalMin(value = "-9999999.999", message = "Y 좌표는 -9999999.999 이상이어야 합니다.")
        @DecimalMax(value = "9999999.999", message = "Y 좌표는 9999999.999 이하여야 합니다.")
        BigDecimal positionY,
        
        Map<String, Object> properties
    ) {
        // 기존 서비스 테스트와의 소스 호환을 위한 임시 생성자. API 요청은 nodeId를 사용한다.
        public NodeInfoReqDTO(
            String nodeName,
            String componentType,
            BigDecimal positionX,
            BigDecimal positionY,
            Map<String, Object> properties
        ) {
            this(nodeName, nodeName, componentType, positionX, positionY, properties);
        }
    }
}
