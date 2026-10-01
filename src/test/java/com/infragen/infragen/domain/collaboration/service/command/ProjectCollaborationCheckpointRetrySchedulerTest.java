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
    private ProjectCollaborationCheckpointFailureService failureService;

    @Test
    @DisplayName("scheduler는 failure마다 id로 retry를 위임한다")
    void retryFailedCheckpoints_DelegatesRetryById() {
        // given
        ProjectCollaborationCheckpointFailure first = failure(10L);
        ProjectCollaborationCheckpointFailure second = failure(11L);
        when(failureRepository.findTop100ByOrderByCreatedAtAsc()).thenReturn(List.of(first, second));
        ProjectCollaborationCheckpointRetryScheduler scheduler = scheduler(true);

        // when
        scheduler.retryFailedCheckpoints();

        // then
        verify(failureService).retry(10L);
        verify(failureService).retry(11L);
    }

    @Test
    @DisplayName("한 failure의 retry가 예외를 던져도 나머지를 계속 처리한다")
    void retryFailedCheckpoints_RetryThrows_ContinuesNext() {
        // given
        ProjectCollaborationCheckpointFailure first = failure(10L);
        ProjectCollaborationCheckpointFailure second = failure(11L);
        when(failureRepository.findTop100ByOrderByCreatedAtAsc()).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("database unavailable")).when(failureService).retry(10L);
        ProjectCollaborationCheckpointRetryScheduler scheduler = scheduler(true);

        // when
        scheduler.retryFailedCheckpoints();

        // then
        verify(failureService).retry(11L);
    }

    @Test
    @DisplayName("checkpoint 재시도가 비활성화되면 조회하지 않는다")
    void retryFailedCheckpoints_Disabled_DoesNothing() {
        // given
        ProjectCollaborationCheckpointRetryScheduler scheduler = scheduler(false);

        // when
        scheduler.retryFailedCheckpoints();

        // then
        verify(failureRepository, never()).findTop100ByOrderByCreatedAtAsc();
        verify(failureService, never()).retry(any());
    }

    private ProjectCollaborationCheckpointRetryScheduler scheduler(boolean enabled) {
        return new ProjectCollaborationCheckpointRetryScheduler(
                properties(enabled),
                failureRepository,
                failureService
        );
    }

    private CollaborationCompactionProperties properties(boolean enabled) {
        CollaborationCompactionProperties properties = new CollaborationCompactionProperties();
        properties.setEnabled(enabled);
        return properties;
    }

    private ProjectCollaborationCheckpointFailure failure(Long id) {
        Project project = Project.builder().title("project").status(ProjectStatus.DRAFT).build();
        ReflectionTestUtils.setField(project, "id", 1L);
        Member member = Member.builder().nickname("owner").isActive(true).build();
        ReflectionTestUtils.setField(member, "id", 2L);
        ProjectCollaborationCheckpointFailure failure = ProjectCollaborationCheckpointFailure.builder()
                .project(project)
                .member(member)
                .serverVersion(50L)
                .graphPayload(Map.of("projectId", 1L))
                .attemptCount(1)
                .lastError("previous failure")
                .build();
        ReflectionTestUtils.setField(failure, "id", id);
        return failure;
    }
}
