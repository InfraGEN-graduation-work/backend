package com.infragen.infragen.domain.project.dto.response;

import java.math.BigDecimal;
import java.util.Map;

import lombok.Builder;

public class ProjectNodeResDTO {
    @Builder
    public record NodeInfoResDTO(
        String nodeId,
        String nodeName,
        String componentType,
        BigDecimal positionX,
        BigDecimal positionY,
        Map<String, Object> properties,
        Long id
    ) {
        // 기존 DB id 기반 생성 코드와의 소스 호환용 생성자. 신규 응답은 nodeId를 사용한다.
        public NodeInfoResDTO(
            Long id,
            String nodeName,
            String componentType,
            BigDecimal positionX,
            BigDecimal positionY,
            Map<String, Object> properties
        ) {
            this(null, nodeName, componentType, positionX, positionY, properties, id);
        }

        public NodeInfoResDTO(
            String nodeId,
            String nodeName,
            String componentType,
            BigDecimal positionX,
            BigDecimal positionY,
            Map<String, Object> properties
        ) {
            this(nodeId, nodeName, componentType, positionX, positionY, properties, null);
        }
    }
}
