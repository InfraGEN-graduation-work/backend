package com.infragen.infragen.domain.project.dto.response;

import lombok.Builder;

public class GeneratedFileResDTO {
    @Builder
    public record FileInfoResDTO(
        Long fileId,
        String fileName,
        String filePath,
        Integer fileSize
    ) {
    }
}
