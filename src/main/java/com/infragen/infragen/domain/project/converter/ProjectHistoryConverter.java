package com.infragen.infragen.domain.project.converter;

import java.util.List;

import com.infragen.infragen.domain.project.dto.response.ProjectHistoryResDTO;
import com.infragen.infragen.domain.project.dto.response.GeneratedFileResDTO;
import com.infragen.infragen.domain.project.entity.GeneratedFile;
import com.infragen.infragen.domain.project.entity.ProjectHistory;

public class ProjectHistoryConverter {

    public static ProjectHistoryResDTO.HistoryPreviewResDTO toHistoryPreviewResDTO(ProjectHistory history) {
        return ProjectHistoryResDTO.HistoryPreviewResDTO.builder()
            .historyId(history.getId())
            .versionName(history.getVersionName())
            .description(history.getDescription())
            .createdAt(history.getCreatedAt())
            .build();
    }

    public static ProjectHistoryResDTO.HistoryPreviewListResDTO toHistoryPreviewListResDTO(
        List<ProjectHistory> historyList
    ) {
        List<ProjectHistoryResDTO.HistoryPreviewResDTO> previewList = historyList.stream()
            .map(ProjectHistoryConverter::toHistoryPreviewResDTO)
            .toList();

        return ProjectHistoryResDTO.HistoryPreviewListResDTO.builder()
            .historyList(previewList)
            .build();
    }

    public static GeneratedFileResDTO.FileInfoResDTO toFileInfoResDTO(GeneratedFile file) {
        return GeneratedFileResDTO.FileInfoResDTO.builder()
            .fileId(file.getId())
            .fileName(file.getFileName())
            .filePath(file.getFilePath())
            .fileSize(file.getFileSize())
            .build();
    }

    public static ProjectHistoryResDTO.HistoryDetailResDTO toHistoryDetailResDTO(
        ProjectHistory history,
        List<GeneratedFile> files
    ) {
        List<GeneratedFileResDTO.FileInfoResDTO> fileInfoList = files.stream()
            .map(ProjectHistoryConverter::toFileInfoResDTO)
            .toList();

        return ProjectHistoryResDTO.HistoryDetailResDTO.builder()
            .historyId(history.getId())
            .versionName(history.getVersionName())
            .description(history.getDescription())
            .createdAt(history.getCreatedAt())
            .generatedFileList(fileInfoList)
            .build();
    }
}
