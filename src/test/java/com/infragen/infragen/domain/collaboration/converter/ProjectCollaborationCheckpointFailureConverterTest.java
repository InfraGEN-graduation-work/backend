package com.infragen.infragen.domain.collaboration.converter;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationCheckpointFailure;
import com.infragen.infragen.domain.collaboration.event.ProjectCollaborationCheckpointEvent;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectCollaborationCheckpointFailureConverterTest {
    @Test
    @DisplayName("checkpoint event와 연관 entity를 retry 저장 entity로 변환한다")
    void toEntity_EventAndResolvedEntities_MapsRetryPayload() {
        // given
        ProjectCollaborationCheckpointEvent event = new ProjectCollaborationCheckpointEvent(
                1L,
                2L,
                50L,
                Map.of("projectId", 1L)
        );
        Project project = Project.builder().title("project").status(ProjectStatus.DRAFT).build();
        Member member = Member.builder().nickname("owner").isActive(true).build();

        // when
        ProjectCollaborationCheckpointFailure result =
                ProjectCollaborationCheckpointFailureConverter.toEntity(event, project, member);

        // then
        assertEquals(project, result.getProject());
        assertEquals(member, result.getMember());
        assertEquals(50L, result.getServerVersion());
        assertEquals(Map.of("projectId", 1L), result.getGraphPayload());
        assertEquals(0, result.getAttemptCount());
    }
}
