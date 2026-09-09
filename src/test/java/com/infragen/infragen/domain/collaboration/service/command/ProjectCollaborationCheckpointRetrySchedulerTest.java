package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationCheckpointFailure;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationCheckpointFailureRepository;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.global.properties.CollaborationCompactionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCollaborationCheckpointRetrySchedulerTest {
    @Mock
    private ProjectCollaborationCheckpointFailureRepository failureRepository;

    @Mock
    private ProjectCollaborationSnapshotWriter snapshotWriter;

    @Mock
    private ProjectCollaborationCheckpointFailureService failureService;

    @Test
    @DisplayName("checkpoint retry가 성공하면 failure row를 삭제한다")
    void retryFailedCheckpoints_Success_DeletesFailure() {
        // given
        CollaborationCompactionProperties properties = properties(true);
        ProjectCollaborationCheckpointFailure failure = failure();
        when(failureRepository.findTop100ByOrderByCreatedAtAsc()).thenReturn(List.of(failure));
        ProjectCollaborationCheckpointRetryScheduler scheduler = new ProjectCollaborationCheckpointRetryScheduler(
                properties,
                failureRepository,
                snapshotWriter,
                failureService
        );

        // when
        scheduler.retryFailedCheckpoints();

        // then
        verify(snapshotWriter).write(any());
        verify(failureRepository).delete(failure);
        verify(failureService, never()).recordRetryFailure(any(), any());
    }

    @Test
    @DisplayName("checkpoint retry가 실패하면 재시도 횟수와 오류를 기록한다")
    void retryFailedCheckpoints_Failure_RecordsRetryFailure() {
        // given
        CollaborationCompactionProperties properties = properties(true);
        ProjectCollaborationCheckpointFailure failure = failure();
        RuntimeException exception = new IllegalStateException("database unavailable");
        when(failureRepository.findTop100ByOrderByCreatedAtAsc()).thenReturn(List.of(failure));
        doThrow(exception).when(snapshotWriter).write(any());
        ProjectCollaborationCheckpointRetryScheduler scheduler = new ProjectCollaborationCheckpointRetryScheduler(
                properties,
                failureRepository,
                snapshotWriter,
                failureService
        );

        // when
        scheduler.retryFailedCheckpoints();

        // then
        verify(failureService).recordRetryFailure(failure, exception);
        verify(failureRepository, never()).delete(failure);
    }

    private CollaborationCompactionProperties properties(boolean enabled) {
        CollaborationCompactionProperties properties = new CollaborationCompactionProperties();
        properties.setEnabled(enabled);
        return properties;
    }

    private ProjectCollaborationCheckpointFailure failure() {
        Project project = Project.builder().title("project").status(ProjectStatus.DRAFT).build();
        ReflectionTestUtils.setField(project, "id", 1L);
        Member member = Member.builder().nickname("owner").isActive(true).build();
        ReflectionTestUtils.setField(member, "id", 2L);
        return ProjectCollaborationCheckpointFailure.builder()
                .project(project)
                .member(member)
                .serverVersion(50L)
                .graphPayload(Map.of("projectId", 1L))
                .attemptCount(1)
                .lastError("previous failure")
                .build();
    }
}
