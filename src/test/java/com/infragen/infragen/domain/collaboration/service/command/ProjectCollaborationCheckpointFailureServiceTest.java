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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
}
