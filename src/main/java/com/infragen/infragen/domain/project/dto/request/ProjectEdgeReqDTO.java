package com.infragen.infragen.domain.project.dto.request;

import jakarta.validation.constraints.NotBlank;

public class ProjectEdgeReqDTO {
    // 캔버스 저장 및 수정 시 사용되는 edge DTO
    public record EdgeInfoReqDTO(
        @NotBlank(message = "시작 노드 이름은 필수입니다.")
        String sourceNodeName,
        
        @NotBlank(message = "대상 노드 이름은 필수입니다.")
        String targetNodeName
    ) {}
}