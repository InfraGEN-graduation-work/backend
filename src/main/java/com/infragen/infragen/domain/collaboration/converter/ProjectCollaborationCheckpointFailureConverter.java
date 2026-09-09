package com.infragen.infragen.domain.collaboration.converter;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationCheckpointFailure;
import com.infragen.infragen.domain.collaboration.event.ProjectCollaborationCheckpointEvent;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.entity.Project;

public final class ProjectCollaborationCheckpointFailureConverter {
    private ProjectCollaborationCheckpointFailureConverter() {
    }

    /**
     * checkpoint failure event와 조회된 연관 entity를 retry 저장 entity로 변환한다.
     */
    public static ProjectCollaborationCheckpointFailure toEntity(
            ProjectCollaborationCheckpointEvent event,
            Project project,
            Member member
    ) {
        return ProjectCollaborationCheckpointFailure.builder()
                .project(project)
                .member(member)
                .serverVersion(event.serverVersion())
                .graphPayload(event.graphPayload())
                .attemptCount(0)
                .lastError(null)
                .build();
    }
}
