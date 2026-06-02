package com.infragen.infragen.domain.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

public class ProjectHistoryReqDTO {
    @Builder
    public record CreateHistoryReqDTO(
        @NotBlank(message = "버전 이름은 필수 입력 항목입니다.")
        String versionName,
        String description
    ) {
    }
}
