package com.infragen.infragen.domain.collaboration.event;

import java.util.List;

import com.infragen.infragen.domain.collaboration.dto.response.CollaborationSnapshotResDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;

/**
 * owner-only PUT 또는 metadata PATCH 결과의 전체 graph와 version을 원래 transaction 안에서 전달한다.
 * commit 전에는 snapshot 저장에, commit 성공 후에는 room resync 전송에 사용한다.
 * 새 snapshot 저장을 수반하므로 단순 알림 재전송 용도로 발행하지 않는다.
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
