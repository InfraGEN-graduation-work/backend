package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationCheckpointFailure;
import com.infragen.infragen.domain.collaboration.event.ProjectCollaborationCheckpointEvent;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationCheckpointFailureRepository;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCollaborationCheckpointFailureServiceTest {
    @Mock
    private ProjectCollaborationCheckpointFailureRepository failureRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private MemberQueryService memberQueryService;

    @Mock
    private ProjectCollaborationSnapshotWriter snapshotWriter;

    @InjectMocks
    private ProjectCollaborationCheckpointFailureService service;

    @Test
    @DisplayName("새 checkpoint 실패를 payload와 재시도 횟수와 함께 저장한다")
    void record_NewFailure_PersistsPayloadAndAttempt() {
        // given
        Project project = Project.builder().title("project").status(ProjectStatus.DRAFT).build();
        Member member = Member.builder().nickname("owner").isActive(true).build();
        ProjectCollaborationCheckpointEvent event = new ProjectCollaborationCheckpointEvent(
                1L,
                2L,
                50L,
                Map.of("projectId", 1L)
        );
        when(failureRepository.findByProjectIdAndServerVersion(1L, 50L))
                .thenReturn(Optional.empty());
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(memberQueryService.findById(2L)).thenReturn(member);
        when(failureRepository.save(any(ProjectCollaborationCheckpointFailure.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        service.record(event, new IllegalStateException("database unavailable"));

        // then
        ArgumentCaptor<ProjectCollaborationCheckpointFailure> captor =
                ArgumentCaptor.forClass(ProjectCollaborationCheckpointFailure.class);
        verify(failureRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getAttemptCount());
        assertEquals("database unavailable", captor.getValue().getLastError());
        assertEquals(Map.of("projectId", 1L), captor.getValue().getGraphPayload());
    }

    @Test
    @DisplayName("retry가 성공하면 같은 payload로 snapshot을 저장하고 failure를 삭제한다")
    void retry_Success_WritesSnapshotAndDeletesFailure() {
        // given
        ProjectCollaborationCheckpointFailure failure = failure();
        when(failureRepository.findById(5L)).thenReturn(Optional.of(failure));

        // when
        service.retry(5L);

        // then
        verify(snapshotWriter).write(new ProjectCollaborationCheckpointEvent(
                1L, 2L, 50L, Map.of("projectId", 1L)
        ));
        verify(failureRepository).delete(failure);
    }

    @Test
    @DisplayName("retry가 실패하면 재조회한 entity의 attemptCount와 lastError를 갱신하고 삭제하지 않는다")
    void retry_Failure_RecordsAttemptAndKeepsFailure() {
        // given
        ProjectCollaborationCheckpointFailure failure = failure();
        when(failureRepository.findById(5L)).thenReturn(Optional.of(failure));
        doThrow(new IllegalStateException("database unavailable")).when(snapshotWriter).write(any());

        // when
        service.retry(5L);

        // then
        assertAll(
                () -> assertEquals(2, failure.getAttemptCount()),
                () -> assertEquals("database unavailable", failure.getLastError())
        );
        verify(failureRepository, never()).delete(failure);
    }

    @Test
    @DisplayName("이미 삭제된 failure id면 retry하지 않는다")
    void retry_FailureNotFound_DoesNothing() {
        // given
        when(failureRepository.findById(5L)).thenReturn(Optional.empty());

        // when
        service.retry(5L);

        // then
        verify(snapshotWriter, never()).write(any());
        verify(failureRepository, never()).delete(any());
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
