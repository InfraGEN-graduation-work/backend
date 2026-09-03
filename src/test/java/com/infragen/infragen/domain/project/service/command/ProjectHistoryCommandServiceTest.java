package com.infragen.infragen.domain.project.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.project.dto.request.ProjectHistoryReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectHistoryResDTO;
import com.infragen.infragen.domain.project.entity.GeneratedFile;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectHistory;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectHistoryRepository;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;

@ExtendWith(MockitoExtension.class)
class ProjectHistoryCommandServiceTest {
    @Mock
    private ProjectQueryService projectQueryService;

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

        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(project);
        when(projectHistoryRepository.countByProjectId(projectId)).thenReturn(0L);
        when(projectHistoryRepository.save(any(ProjectHistory.class))).thenReturn(savedHistory);

        ProjectHistoryResDTO.HistoryPreviewResDTO result = projectHistoryCommandService.createHistory(projectId, request, memberId);

        assertNotNull(result);
        assertEquals(200L, result.historyId());
        assertEquals("v1", result.versionName());
        assertEquals("Initial commit", result.description());
        assertNotNull(result.createdAt());

        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(projectHistoryRepository).countByProjectId(projectId);
        verify(projectHistoryRepository).save(any(ProjectHistory.class));
    }

    @Test
    @DisplayName("히스토리 생성 - 프로젝트가 없거나 권한 불일치 시 예외 발생")
    void createHistory_ProjectNotFound_ThrowsException() {
        Long memberId = 1L;
        Long projectId = 100L;
        ProjectHistoryReqDTO.CreateHistoryReqDTO request = new ProjectHistoryReqDTO.CreateHistoryReqDTO("Initial commit");

        when(projectQueryService.getOwnedProject(projectId, memberId))
            .thenThrow(new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));

        ProjectException exception = assertThrows(ProjectException.class,
                () -> projectHistoryCommandService.createHistory(projectId, request, memberId));

        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(projectHistoryRepository, never()).countByProjectId(projectId);
        verify(projectHistoryRepository, never()).save(any(ProjectHistory.class));
    }

    @Test
    @DisplayName("생성 이력 저장 - 성공 시 historyId 반환 및 파일 본문·경로 저장")
    void saveGeneratedHistory_Success() {
        Long memberId = 1L;
        Long projectId = 100L;
        List<IaCFileDTO.FileContentResDTO> generatedFiles = List.of(
            IaCFileDTO.FileContentResDTO.builder()
                .fileName("local/docker-compose.yml")
                .content("services:\n  mysql:\n    image: mysql:8.0\n")
                .build(),
            IaCFileDTO.FileContentResDTO.builder()
                .fileName("local/.env")
                .content("SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/appdb\n")
                .build(),
            IaCFileDTO.FileContentResDTO.builder()
                .fileName("cloud/aws/terraform/main.tf")
                .content("resource \"aws_vpc\" \"main\" {}\n")
                .build(),
            IaCFileDTO.FileContentResDTO.builder()
                .fileName("cloud/Dockerfile")
                .content("FROM eclipse-temurin:17-jre\n")
                .build()
        );

        Project project = Project.builder()
            .title("Test Project")
            .description("Test Description")
            .build();
        ReflectionTestUtils.setField(project, "id", projectId);

        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(project);
        when(projectHistoryRepository.countByProjectId(projectId)).thenReturn(1L);
        when(projectHistoryRepository.save(any(ProjectHistory.class))).thenAnswer(invocation -> {
            ProjectHistory history = invocation.getArgument(0);
            ReflectionTestUtils.setField(history, "id", 300L);
            return history;
        });

        Long historyId = projectHistoryCommandService.saveGeneratedHistory(
            projectId, memberId, generatedFiles);

        assertEquals(300L, historyId);
        
        // 저장된 ProjectHistory 객체를 확인
        ArgumentCaptor<ProjectHistory> historyCaptor = ArgumentCaptor.forClass(ProjectHistory.class);
        verify(projectHistoryRepository).save(historyCaptor.capture());

        ProjectHistory savedHistory = historyCaptor.getValue();
        assertEquals("v2", savedHistory.getVersionName());
        assertNull(savedHistory.getDescription());
        assertEquals(project, savedHistory.getProject());
        assertEquals(4, savedHistory.getGeneratedFileList().size());

        GeneratedFile composeFile = savedHistory.getGeneratedFileList().get(0);
        assertEquals("local/docker-compose.yml", composeFile.getFileName());
        assertEquals("services:\n  mysql:\n    image: mysql:8.0\n", composeFile.getContent());
        assertEquals("projects/100/histories/v2/local/docker-compose.yml", composeFile.getFilePath());
        assertEquals(composeFile.getContent().getBytes(StandardCharsets.UTF_8).length, composeFile.getFileSize());

        GeneratedFile envFile = savedHistory.getGeneratedFileList().get(1);
        assertEquals("local/.env", envFile.getFileName());
        assertEquals("SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/appdb\n", envFile.getContent());
        assertEquals("projects/100/histories/v2/local/.env", envFile.getFilePath());

        GeneratedFile terraformFile = savedHistory.getGeneratedFileList().get(2);
        assertEquals("cloud/aws/terraform/main.tf", terraformFile.getFileName());
        assertEquals("projects/100/histories/v2/cloud/aws/terraform/main.tf", terraformFile.getFilePath());

        GeneratedFile dockerfile = savedHistory.getGeneratedFileList().get(3);
        assertEquals("cloud/Dockerfile", dockerfile.getFileName());
        assertEquals("projects/100/histories/v2/cloud/Dockerfile", dockerfile.getFilePath());

        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(projectHistoryRepository).countByProjectId(projectId);
    }

    @Test
    @DisplayName("생성 이력 저장 - 프로젝트가 없거나 권한 불일치 시 예외 발생")
    void saveGeneratedHistory_ProjectNotFound_ThrowsException() {
        Long memberId = 1L;
        Long projectId = 100L;
        List<IaCFileDTO.FileContentResDTO> generatedFiles = List.of(
            IaCFileDTO.FileContentResDTO.builder()
                .fileName("docker-compose.yml")
                .content("services: {}")
                .build()
        );

        when(projectQueryService.getOwnedProject(projectId, memberId))
            .thenThrow(new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));

        ProjectException exception = assertThrows(ProjectException.class,
            () -> projectHistoryCommandService.saveGeneratedHistory(projectId, memberId, generatedFiles));

        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(projectHistoryRepository, never()).countByProjectId(projectId);
        verify(projectHistoryRepository, never()).save(any(ProjectHistory.class));
    }
}
