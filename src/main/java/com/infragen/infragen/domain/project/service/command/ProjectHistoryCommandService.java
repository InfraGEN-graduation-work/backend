package com.infragen.infragen.domain.project.service.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.project.converter.ProjectHistoryConverter;
import com.infragen.infragen.domain.project.dto.request.ProjectHistoryReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectHistoryResDTO;
import com.infragen.infragen.domain.project.entity.GeneratedFile;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectHistory;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectHistoryRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectHistoryCommandService {
    private final ProjectRepository projectRepository;
    private final ProjectHistoryRepository projectHistoryRepository;

    @Transactional
    public ProjectHistoryResDTO.HistoryPreviewResDTO createHistory(
        Long projectId,
        ProjectHistoryReqDTO.CreateHistoryReqDTO request,
        Long memberId
    ) {
        log.info("프로젝트 히스토리 생성 요청: projectId={}, memberId={}", projectId, memberId);

        Project project = projectRepository.findByIdAndMemberId(projectId, memberId)
            .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));

        // 기존 히스토리 개수를 세어 v1, v2 형태로 버전명 자동 매핑
        long count = projectHistoryRepository.countByProjectId(projectId);
        String versionName = "v" + (count + 1);

        ProjectHistory history = ProjectHistory.builder()
            .versionName(versionName)
            .description(request.description())
            .project(project)
            .build();

        // TODO: 추후 캔버스 파서 엔진 및 스토리지 서비스 연동 시 동적 파일 리스트 생성 및 업로드 로직으로 대체 예정
        GeneratedFile dummyCompose = GeneratedFile.builder()
            .fileName("docker-compose.yml")
            .filePath("projects/" + projectId + "/histories/" + versionName + "/docker-compose.yml") // 추후 동적으로 생성된 파일 이름 추가
            .fileSize(1024) // 가상의 1KB 크기 지정
            .build();
        
        history.addGeneratedFile(dummyCompose);

        // history와 연관된 generatedFile이 함께 영속화
        ProjectHistory savedHistory = projectHistoryRepository.save(history);
        log.info("프로젝트 히스토리 생성 완료: historyId={}, versionName={}", savedHistory.getId(), versionName);

        return ProjectHistoryConverter.toHistoryPreviewResDTO(savedHistory);
    }
}
