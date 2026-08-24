package com.infragen.infragen.domain.project.service.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.infragen.infragen.domain.project.dto.response.ProjectHistoryResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectHistory;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.ProjectHistoryException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.exception.code.error.ProjectHistoryErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectHistoryRepository;

@ExtendWith(MockitoExtension.class)
class ProjectHistoryQueryServiceTest {
    @Mock
    private ProjectQueryService projectQueryService;

    @Mock
    private ProjectHistoryRepository projectHistoryRepository;

    @InjectMocks
    private ProjectHistoryQueryService projectHistoryQueryService;

    @Test
    @DisplayName("히스토리 목록 조회 - 성공 시 목록 반환")
    void getHistories_Success() {
        Long memberId = 1L;
        Long projectId = 100L;

        Project project = Project.builder().build();
        ReflectionTestUtils.setField(project, "id", projectId);

        ProjectHistory history1 = ProjectHistory.builder()
                .versionName("v1")
                .description("First save")
                .project(project)
                .build();
        ReflectionTestUtils.setField(history1, "id", 201L);
        ReflectionTestUtils.setField(history1, "createdAt", LocalDateTime.now());

        ProjectHistory history2 = ProjectHistory.builder()
                .versionName("v2")
                .description("Second save")
                .project(project)
                .build();
        ReflectionTestUtils.setField(history2, "id", 202L);
        ReflectionTestUtils.setField(history2, "createdAt", LocalDateTime.now().plusHours(1));

        List<ProjectHistory> historyList = List.of(history2, history1);

        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(project);
        when(projectHistoryRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(historyList);

        ProjectHistoryResDTO.HistoryPreviewListResDTO result = projectHistoryQueryService.getHistories(projectId, memberId);

        assertNotNull(result);
        assertEquals(2, result.historyList().size());
        assertEquals("v2", result.historyList().get(0).versionName());
        assertEquals("v1", result.historyList().get(1).versionName());

        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(projectHistoryRepository).findAllByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Test
    @DisplayName("히스토리 목록 조회 - 프로젝트가 없거나 권한 불일치 시 예외 발생")
    void getHistories_ProjectNotFound_ThrowsException() {
        Long memberId = 1L;
        Long projectId = 100L;

        when(projectQueryService.getOwnedProject(projectId, memberId))
            .thenThrow(new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));

        ProjectException exception = assertThrows(ProjectException.class,
                () -> projectHistoryQueryService.getHistories(projectId, memberId));

        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(projectHistoryRepository, never()).findAllByProjectIdOrderByCreatedAtDesc(anyLong());
    }

    @Test
    @DisplayName("히스토리 상세 조회 - 성공 시 파일 목록 포함 상세 정보 반환")
    void getHistoryDetail_Success() {
        Long memberId = 1L;
        Long projectId = 100L;
        Long historyId = 200L;

        Project project = Project.builder().build();
        ReflectionTestUtils.setField(project, "id", projectId);

        ProjectHistory history = ProjectHistory.builder()
                .versionName("v1")
                .description("First save")
                .project(project)
                .build();
        ReflectionTestUtils.setField(history, "id", historyId);
        ReflectionTestUtils.setField(history, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(history, "generatedFileList", new ArrayList<>());

        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(project);
        when(projectHistoryRepository.findByIdAndProjectId(historyId, projectId)).thenReturn(java.util.Optional.of(history));

        ProjectHistoryResDTO.HistoryDetailResDTO result = projectHistoryQueryService.getHistoryDetail(projectId, historyId, memberId);

        assertNotNull(result);
        assertEquals(historyId, result.historyId());
        assertEquals("v1", result.versionName());
        assertEquals("First save", result.description());
        assertEquals(0, result.generatedFileList().size());

        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(projectHistoryRepository).findByIdAndProjectId(historyId, projectId);
    }

    @Test
    @DisplayName("히스토리 상세 조회 - 프로젝트 권한 없을 시 예외 발생")
    void getHistoryDetail_ProjectNotFound_ThrowsException() {
        Long memberId = 1L;
        Long projectId = 100L;
        Long historyId = 200L;

        when(projectQueryService.getOwnedProject(projectId, memberId))
            .thenThrow(new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));

        ProjectException exception = assertThrows(ProjectException.class,
                () -> projectHistoryQueryService.getHistoryDetail(projectId, historyId, memberId));

        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(projectHistoryRepository, never()).findByIdAndProjectId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("히스토리 상세 조회 - 히스토리 비존재 시 예외 발생")
    void getHistoryDetail_HistoryNotFound_ThrowsException() {
        Long memberId = 1L;
        Long projectId = 100L;
        Long historyId = 999L;

        Project project = Project.builder().build();
        ReflectionTestUtils.setField(project, "id", projectId);

        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(project);
        when(projectHistoryRepository.findByIdAndProjectId(historyId, projectId)).thenReturn(java.util.Optional.empty());

        ProjectHistoryException exception = assertThrows(ProjectHistoryException.class,
                () -> projectHistoryQueryService.getHistoryDetail(projectId, historyId, memberId));

        assertEquals(ProjectHistoryErrorCode.PROJECT_HISTORY_NOT_FOUND, exception.getCode());
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(projectHistoryRepository).findByIdAndProjectId(historyId, projectId);
    }
}
