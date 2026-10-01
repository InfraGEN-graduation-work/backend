package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationState;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
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

    @Mock
    private ProjectCollaborationOperationRepository operationRepository;

    @InjectMocks
    private ProjectCollaborationVersionService versionService;

    @Test
    @DisplayName("state가 없으면 project를 잠그고 초기 state를 생성한다")
    void issueNextVersion_withoutState_locksProjectAndCreatesInitialState() {
        // given
        Long projectId = 1L;
        Project project = project();
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
        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(stateRepository.findByProjectIdForUpdate(projectId)).thenReturn(Optional.of(state));

        // when
        Long serverVersion = versionService.issueNextVersion(projectId, 1L);

        // then
        assertEquals(2L, serverVersion);
        org.mockito.InOrder locks = org.mockito.Mockito.inOrder(projectRepository, stateRepository);
        locks.verify(projectRepository).findByIdForUpdate(projectId);
        locks.verify(stateRepository).findByProjectIdForUpdate(projectId);
    }

    @Test
    @DisplayName("현재 version보다 미래인 baseVersion은 충돌로 거부한다")
    void issueNextVersion_withFutureBaseVersion_throwsVersionConflict() {
        // given
        Long projectId = 1L;
        Project project = project();
        ProjectCollaborationState state = new ProjectCollaborationState(project);
        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
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
        when(projectRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(state.getProject()));
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
        when(projectRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(state.getProject()));
        when(stateRepository.findByProjectIdForUpdate(1L)).thenReturn(Optional.of(state));

        // when
        Long version = versionService.issueNextVersionForFullReplace(1L, 1L);

        // then
        assertEquals(2L, version);
        verify(stateRepository, org.mockito.Mockito.never()).findByProjectId(1L);
    }

    @Test
    @DisplayName("compaction 경계보다 오래된 baseVersion의 operation은 중복 확인 없이 거부한다")
    void issueNextVersion_BaseVersionBelowCompactedVersion_Rejects() {
        // given
        ProjectCollaborationState state = stateAt(250L, 150L);
        when(projectRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(state.getProject()));
        when(stateRepository.findByProjectIdForUpdate(1L)).thenReturn(Optional.of(state));
        when(operationRepository.findByProjectIdAndOperationId(1L, "op-a")).thenReturn(Optional.empty());

        // when
        var exception = assertThrows(CollaborationException.class,
                () -> versionService.issueNextVersion(1L, 149L, "op-a"));

        // then
        assertEquals(CollaborationErrorCode.VERSION_CONFLICT, exception.getCode());
        assertEquals(250L, state.getServerVersion());
    }

    @Test
    @DisplayName("baseVersion이 compaction 경계와 같으면 새 operation으로 발급한다")
    void issueNextVersion_BaseVersionEqualsCompactedVersion_IssuesVersion() {
        // given
        ProjectCollaborationState state = stateAt(250L, 150L);
        when(projectRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(state.getProject()));
        when(stateRepository.findByProjectIdForUpdate(1L)).thenReturn(Optional.of(state));
        when(operationRepository.findByProjectIdAndOperationId(1L, "op-a")).thenReturn(Optional.empty());

        // when
        var issuance = versionService.issueNextVersion(1L, 150L, "op-a");

        // then
        assertEquals(251L, issuance.serverVersion());
    }

    @Test
    @DisplayName("로그가 남아 있는 operationId는 경계보다 오래된 baseVersion이어도 기존 operation을 돌려준다")
    void issueNextVersion_ExistingLogBelowBoundary_ReturnsExisting() {
        // given
        ProjectCollaborationState state = stateAt(250L, 150L);
        ProjectCollaborationOperation existing = org.mockito.Mockito.mock(ProjectCollaborationOperation.class);
        when(projectRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(state.getProject()));
        when(stateRepository.findByProjectIdForUpdate(1L)).thenReturn(Optional.of(state));
        when(operationRepository.findByProjectIdAndOperationId(1L, "op-a")).thenReturn(Optional.of(existing));

        // when
        var issuance = versionService.issueNextVersion(1L, 100L, "op-a");

        // then
        assertEquals(existing, issuance.existingOperation());
        assertEquals(250L, state.getServerVersion());
    }

    private ProjectCollaborationState stateAt(long serverVersion, long compactedVersion) {
        var state = new ProjectCollaborationState(project());
        for (long i = 0; i < serverVersion; i++) {
            state.advanceServerVersion();
        }
        state.raiseCompactedVersion(compactedVersion);
        return state;
    }

    private Project project() {
        return Project.builder()
                .title("project")
                .status(ProjectStatus.DRAFT)
                .build();
    }
}
