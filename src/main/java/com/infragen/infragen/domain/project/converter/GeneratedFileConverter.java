package com.infragen.infragen.domain.project.converter;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.project.dto.response.GeneratedFileResDTO;
import com.infragen.infragen.domain.project.entity.GeneratedFile;

public class GeneratedFileConverter {

    public static GeneratedFile toEntity(
        IaCFileDTO.FileContentResDTO source,
        Long projectId,
        String versionName
    ) {
        String content = source.content();
        return GeneratedFile.builder()
            .fileName(source.fileName())
            .filePath(buildFilePath(projectId, versionName, source.fileName()))
            .fileSize(resolveFileSize(content))
            .content(content)
            .build();
    }

    public static List<GeneratedFile> toEntityList(
        List<IaCFileDTO.FileContentResDTO> sources,
        Long projectId,
        String versionName
    ) {
        if (sources == null) {
            return List.of();
        }
        return sources.stream()
            .map(source -> toEntity(source, projectId, versionName))
            .toList();
    }

    public static GeneratedFileResDTO.FileInfoResDTO toFileInfoResDTO(GeneratedFile file) {
        return GeneratedFileResDTO.FileInfoResDTO.builder()
            .fileId(file.getId())
            .fileName(file.getFileName())
            .filePath(file.getFilePath())
            .fileSize(file.getFileSize())
            .content(file.getContent())
            .build();
    }

    private static int resolveFileSize(String content) {
        if (content == null) {
            return 0;
        }
        return content.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String buildFilePath(Long projectId, String versionName, String fileName) {
        return "projects/" + projectId + "/histories/" + versionName + "/" + fileName;
    }
}
