package com.infragen.infragen.domain.project.service.query;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.infragen.infragen.domain.project.converter.ProjectHistoryConverter;
import com.infragen.infragen.domain.project.dto.response.ProjectHistoryResDTO;
import com.infragen.infragen.domain.project.entity.ProjectHistory;
import com.infragen.infragen.domain.project.exception.ProjectHistoryException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectHistoryErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectHistoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectHistoryQueryService {
    private final ProjectAccessService projectAccessService;
    private final ProjectHistoryRepository projectHistoryRepository;

    /** 현재 프로젝트 참여자가 저장·생성 이력 목록을 조회한다. */
    @Transactional(readOnly = true)
    public ProjectHistoryResDTO.HistoryPreviewListResDTO getHistories(Long projectId, Long memberId) {
        projectAccessService.requireReadAccess(projectId, memberId);

        // 프로젝트에 매핑된 전체 히스토리 내역을 최신순으로 조회
        List<ProjectHistory> historyList = projectHistoryRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId);

        return ProjectHistoryConverter.toHistoryPreviewListResDTO(historyList);
    }

    /** 현재 프로젝트 참여자가 해당 프로젝트의 이력과 생성 파일을 조회한다. */
    @Transactional(readOnly = true)
    public ProjectHistoryResDTO.HistoryDetailResDTO getHistoryDetail(Long projectId, Long historyId, Long memberId) {
        projectAccessService.requireReadAccess(projectId, memberId);

        // 특정 프로젝트에 속한 이력인지 함께 검증하여 히스토리 조회
        ProjectHistory history = projectHistoryRepository.findByIdAndProjectId(historyId, projectId)
            .orElseThrow(() -> new ProjectHistoryException(ProjectHistoryErrorCode.PROJECT_HISTORY_NOT_FOUND));

        return ProjectHistoryConverter.toHistoryDetailResDTO(history, history.getGeneratedFileList());
    }
}
