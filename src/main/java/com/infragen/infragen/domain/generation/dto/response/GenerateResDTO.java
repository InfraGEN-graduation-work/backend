package com.infragen.infragen.domain.generation.dto.response;

import java.util.List;

import lombok.Builder;

public class GenerateResDTO {
    @Builder
    public record GeneratedFileResDTO(
        String fileName,
        String content
    ) {
    }

    @Builder
    public record GenerateResultResDTO(
        List<GeneratedFileResDTO> files,
        Long historyId
    ) {
    }
}
