package com.infragen.infragen.domain.collaboration.event;

import java.util.List;

import com.infragen.infragen.domain.collaboration.dto.response.CollaborationSnapshotResDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;

/**
 * REST PUT으로 materialized graph가 교체된 뒤 collaboration room을 재동기화할 event다.
 */
public record ProjectRoomResyncEvent(
        Long projectId,
        CollaborationSnapshotResDTO.SnapshotResDTO snapshot
) {
    public ProjectRoomResyncEvent(
            Long projectId,
            Long serverVersion,
            ProjectResDTO.ProjectDetailResDTO project
    ) {
        this(
                projectId,
                CollaborationSnapshotResDTO.SnapshotResDTO.builder()
                        .project(project)
                        .graphVersion(serverVersion)
                        .serverVersion(serverVersion)
                        .operations(List.of())
                        .build()
        );
    }
}
