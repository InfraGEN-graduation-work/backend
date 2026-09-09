package com.infragen.infragen.domain.collaboration.converter;

import java.util.Map;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationSnapshot;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class ProjectCollaborationSnapshotConverter {
    private ProjectCollaborationSnapshotConverter() {
    }

    /**
     * project detail graph를 snapshot JSON payload로 변환한다.
     *
     * @param projectDetail 저장할 materialized project graph
     * @param objectMapper JSON payload 변환에 사용할 mapper
     * @return JSON persistence에 사용할 graph payload
     */
    public static Map<String, Object> toGraphPayload(
            ProjectResDTO.ProjectDetailResDTO projectDetail,
            ObjectMapper objectMapper
    ) {
        return objectMapper.convertValue(
                projectDetail,
                new TypeReference<Map<String, Object>>() {
                }
        );
    }

    /**
     * 저장된 snapshot payload를 project detail graph로 복원한다.
     *
     * @param snapshot 복원할 project snapshot
     * @param objectMapper JSON payload 변환에 사용할 mapper
     * @return snapshot version 시점의 project detail graph
     */
    public static ProjectResDTO.ProjectDetailResDTO toProjectDetailResDTO(
            ProjectCollaborationSnapshot snapshot,
            ObjectMapper objectMapper
    ) {
        return objectMapper.convertValue(
                snapshot.getGraphPayload(),
                ProjectResDTO.ProjectDetailResDTO.class
        );
    }
}
