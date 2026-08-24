package com.infragen.infragen.domain.project.service.command;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.project.converter.GeneratedFileConverter;
import com.infragen.infragen.domain.project.converter.ProjectHistoryConverter;
import com.infragen.infragen.domain.project.dto.request.ProjectHistoryReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectHistoryResDTO;
import com.infragen.infragen.domain.project.entity.GeneratedFile;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectHistory;
import com.infragen.infragen.domain.project.repository.ProjectHistoryRepository;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectHistoryCommandService {
    private final ProjectQueryService projectQueryService;
    private final ProjectHistoryRepository projectHistoryRepository;

    // 프로젝트 히스토리 생성
    @Transactional
    public ProjectHistoryResDTO.HistoryPreviewResDTO createHistory(
        Long projectId,
        ProjectHistoryReqDTO.CreateHistoryReqDTO request,
        Long memberId
    ) {
        log.info("프로젝트 히스토리 생성 요청: projectId={}, memberId={}", projectId, memberId);

        Project project = projectQueryService.getOwnedProject(projectId, memberId);
        String versionName = nextVersionName(projectId);

        ProjectHistory history = ProjectHistory.builder()
            .versionName(versionName)
            .description(request.description())
            .project(project)
            .build();

        ProjectHistory savedHistory = projectHistoryRepository.save(history);
        log.info("프로젝트 히스토리 생성 완료: historyId={}, versionName={}", savedHistory.getId(), versionName);

        return ProjectHistoryConverter.toHistoryPreviewResDTO(savedHistory);
    }

    // Generate API — IaC 파일 본문을 포함한 이력 저장 (B2-4에서 호출)
    @Transactional
    public Long saveGeneratedHistory(
        Long projectId,
        Long memberId,
        List<IaCFileDTO.FileContentResDTO> generatedFiles
    ) {
        log.info("생성 이력 저장 요청: projectId={}, memberId={}, fileCount={}",
            projectId, memberId, generatedFiles.size());

        Project project = projectQueryService.getOwnedProject(projectId, memberId);
        String versionName = nextVersionName(projectId);

        ProjectHistory history = ProjectHistory.builder()
            .versionName(versionName)
            .project(project)
            .build();

        for (GeneratedFile generatedFile : GeneratedFileConverter.toEntityList(
            generatedFiles, projectId, versionName)) {
            history.addGeneratedFile(generatedFile);
        }

        ProjectHistory savedHistory = projectHistoryRepository.save(history);
        log.info("생성 이력 저장 완료: historyId={}, versionName={}, fileCount={}",
            savedHistory.getId(), versionName, generatedFiles.size());

        return savedHistory.getId();
    }

    private String nextVersionName(Long projectId) {
        long count = projectHistoryRepository.countByProjectId(projectId);
        return "v" + (count + 1);
    }
}
