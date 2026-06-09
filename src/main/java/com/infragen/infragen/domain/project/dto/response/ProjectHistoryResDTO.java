package com.infragen.infragen.domain.project.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;

public class ProjectHistoryResDTO {

    @Builder
    public record HistoryPreviewResDTO(
        Long historyId,
        String versionName,
        String description,
        LocalDateTime createdAt
    ) {
    }

    @Builder
    public record HistoryPreviewListResDTO(
        List<HistoryPreviewResDTO> historyList
    ) {
    }

    @Builder
    public record HistoryDetailResDTO(
        Long historyId,
        String versionName,
        String description,
        LocalDateTime createdAt,
        List<GeneratedFileResDTO.FileInfoResDTO> generatedFileList
    ) {
    }
}
