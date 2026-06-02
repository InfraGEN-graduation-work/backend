package com.infragen.infragen.domain.project.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.infragen.infragen.domain.project.dto.request.ProjectHistoryReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectHistoryResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectHistory;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectHistoryRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;

@ExtendWith(MockitoExtension.class)
class ProjectHistoryCommandServiceTest {
    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectHistoryRepository projectHistoryRepository;

    @InjectMocks
    private ProjectHistoryCommandService projectHistoryCommandService;

    @Test
    @DisplayName("히스토리 생성 - 성공 시 요약 정보 반환 및 순차 버전 생성")
    void createHistory_Success() {
        Long memberId = 1L;
        Long projectId = 100L;
        ProjectHistoryReqDTO.CreateHistoryReqDTO request = new ProjectHistoryReqDTO.CreateHistoryReqDTO("Initial commit");

        Project project = Project.builder()
                .title("Test Project")
                .description("Test Description")
                .build();
        ReflectionTestUtils.setField(project, "id", projectId);

        ProjectHistory savedHistory = ProjectHistory.builder()
                .versionName("v1")
                .description("Initial commit")
                .project(project)
                .build();
        ReflectionTestUtils.setField(savedHistory, "id", 200L);
        ReflectionTestUtils.setField(savedHistory, "createdAt", LocalDateTime.now());

        when(projectRepository.findByIdAndMemberId(projectId, memberId)).thenReturn(Optional.of(project));
        when(projectHistoryRepository.countByProjectId(projectId)).thenReturn(0L);
        when(projectHistoryRepository.save(any(ProjectHistory.class))).thenReturn(savedHistory);

        ProjectHistoryResDTO.HistoryPreviewResDTO result = projectHistoryCommandService.createHistory(projectId, request, memberId);

        assertNotNull(result);
        assertEquals(200L, result.historyId());
        assertEquals("v1", result.versionName());
        assertEquals("Initial commit", result.description());
        assertNotNull(result.createdAt());

        verify(projectRepository).findByIdAndMemberId(projectId, memberId);
        verify(projectHistoryRepository).countByProjectId(projectId);
        verify(projectHistoryRepository).save(any(ProjectHistory.class));
    }

    @Test
    @DisplayName("히스토리 생성 - 프로젝트가 없거나 권한 불일치 시 예외 발생")
    void createHistory_ProjectNotFound_ThrowsException() {
        Long memberId = 1L;
        Long projectId = 100L;
        ProjectHistoryReqDTO.CreateHistoryReqDTO request = new ProjectHistoryReqDTO.CreateHistoryReqDTO("Initial commit");

        when(projectRepository.findByIdAndMemberId(projectId, memberId)).thenReturn(Optional.empty());

        ProjectException exception = assertThrows(ProjectException.class,
                () -> projectHistoryCommandService.createHistory(projectId, request, memberId));

        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verify(projectRepository).findByIdAndMemberId(projectId, memberId);
        verify(projectHistoryRepository, never()).countByProjectId(projectId);
        verify(projectHistoryRepository, never()).save(any(ProjectHistory.class));
    }
}
