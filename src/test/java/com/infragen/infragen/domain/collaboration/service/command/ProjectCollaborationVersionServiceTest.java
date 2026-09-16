package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationState;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationStateRepository;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCollaborationVersionServiceTest {
    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectCollaborationStateRepository stateRepository;

    @InjectMocks
    private ProjectCollaborationVersionService versionService;

    @Test
    @DisplayName("state가 없으면 project를 잠그고 초기 state를 생성한다")
    void issueNextVersion_withoutState_locksProjectAndCreatesInitialState() {
        // given
        Long projectId = 1L;
        Project project = project();
        when(stateRepository.existsByProjectId(projectId)).thenReturn(false);
        when(projectRepository.findByIdForUpdate(projectId))
                .thenReturn(Optional.of(project));
        when(stateRepository.findByProjectIdForUpdate(projectId)).thenReturn(Optional.empty());
        when(stateRepository.save(any(ProjectCollaborationState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Long serverVersion = versionService.issueNextVersion(projectId, 0L);

        // then
        assertEquals(1L, serverVersion);
        verify(projectRepository).findByIdForUpdate(projectId);
        ArgumentCaptor<ProjectCollaborationState> stateCaptor =
                ArgumentCaptor.forClass(ProjectCollaborationState.class);
        verify(stateRepository).save(stateCaptor.capture());
        assertEquals(project, stateCaptor.getValue().getProject());
    }

    @Test
    @DisplayName("기존 state를 잠근 뒤 다음 serverVersion을 발급한다")
    void issueNextVersion_withExistingState_advancesLockedState() {
        // given
        Long projectId = 1L;
        Project project = project();
        ProjectCollaborationState state = new ProjectCollaborationState(project);
        state.advanceServerVersion();
        when(stateRepository.existsByProjectId(projectId)).thenReturn(true);
        when(stateRepository.findByProjectIdForUpdate(projectId)).thenReturn(Optional.of(state));

        // when
        Long serverVersion = versionService.issueNextVersion(projectId, 1L);

        // then
        assertEquals(2L, serverVersion);
        verify(projectRepository, org.mockito.Mockito.never()).findByIdForUpdate(projectId);
    }

    @Test
    @DisplayName("현재 version보다 미래인 baseVersion은 충돌로 거부한다")
    void issueNextVersion_withFutureBaseVersion_throwsVersionConflict() {
        // given
        Long projectId = 1L;
        Project project = project();
        ProjectCollaborationState state = new ProjectCollaborationState(project);
        when(stateRepository.existsByProjectId(projectId)).thenReturn(true);
        when(stateRepository.findByProjectIdForUpdate(projectId)).thenReturn(Optional.of(state));

        // when
        CollaborationException exception = assertThrows(
                CollaborationException.class,
                () -> versionService.issueNextVersion(projectId, 1L)
        );

        // then
        assertEquals(CollaborationErrorCode.VERSION_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("존재하지 않는 project는 version을 발급하지 않는다")
    void issueNextVersion_withUnknownProject_throwsProjectNotFound() {
        // given
        Long projectId = 1L;
        when(stateRepository.existsByProjectId(projectId)).thenReturn(false);
        when(projectRepository.findByIdForUpdate(projectId))
                .thenReturn(Optional.empty());

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> versionService.issueNextVersion(projectId, 0L)
        );

        // then
        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 2L})
    @DisplayName("정확한 version을 요구하는 수정은 이전·미래 version 모두 거부한다")
    void issueNextVersionForFullReplace_MismatchedVersion_Rejects(long baseVersion) {
        // given
        var state = new ProjectCollaborationState(project());
        state.advanceServerVersion();
        when(stateRepository.existsByProjectId(1L)).thenReturn(true);
        when(stateRepository.findByProjectIdForUpdate(1L)).thenReturn(Optional.of(state));

        // when
        var exception = assertThrows(CollaborationException.class,
                () -> versionService.issueNextVersionForFullReplace(1L, baseVersion));

        // then
        assertEquals(CollaborationErrorCode.VERSION_CONFLICT, exception.getCode());
        assertEquals(1L, state.getServerVersion());
        verify(stateRepository, org.mockito.Mockito.never()).findByProjectId(1L);
    }

    @Test
    @DisplayName("정확한 version을 요구하는 수정은 잠금으로 읽은 version을 증가시킨다")
    void issueNextVersionForFullReplace_CurrentVersion_AdvancesLockedState() {
        // given
        var state = new ProjectCollaborationState(project());
        state.advanceServerVersion();
        when(stateRepository.existsByProjectId(1L)).thenReturn(true);
        when(stateRepository.findByProjectIdForUpdate(1L)).thenReturn(Optional.of(state));

        // when
        Long version = versionService.issueNextVersionForFullReplace(1L, 1L);

        // then
        assertEquals(2L, version);
        verify(stateRepository, org.mockito.Mockito.never()).findByProjectId(1L);
    }

    private Project project() {
        return Project.builder()
                .title("project")
                .status(ProjectStatus.DRAFT)
                .build();
    }
}
