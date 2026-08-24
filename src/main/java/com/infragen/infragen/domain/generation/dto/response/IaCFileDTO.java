package com.infragen.infragen.domain.generation.dto.response;

import java.util.List;

import lombok.Builder;

public class IaCFileDTO {
    @Builder
    public record FileContentResDTO(
        String fileName,
        String content
    ) {
    }

    @Builder
    public record BundleResDTO(
        List<FileContentResDTO> files
    ) {
    }
}
