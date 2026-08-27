package com.infragen.infragen.domain.project.dto.request;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ProjectNodeReqDTO {
    // 캔버스 저장 및 수정 시 사용되는 노드 개별 DTO
    public record NodeInfoReqDTO(
        @NotBlank(message = "노드 ID는 필수입니다.")
        String nodeId,

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
