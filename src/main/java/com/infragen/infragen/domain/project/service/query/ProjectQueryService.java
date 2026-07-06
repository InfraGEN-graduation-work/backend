package com.infragen.infragen.domain.project.service.query;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.project.converter.ProjectConverter;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.entity.ProjectEdge;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectQueryService {
    private final ProjectRepository projectRepository;
    private final ProjectNodeRepository projectNodeRepository;
    private final ProjectEdgeRepository projectEdgeRepository;

    public ProjectResDTO.ProjectPreviewListResDTO getProjects(Long memberId) {
        List<Project> projectList = projectRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId);

        return ProjectConverter.toProjectPreviewListResDTO(projectList);
    }

    // 소유권 검증 후 Project 반환 — Command·Query 공통
    public Project getOwnedProject(Long projectId, Long memberId) {
        return projectRepository.findByIdAndMemberId(projectId, memberId)
            .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    public ProjectResDTO.ProjectDetailResDTO getProjectDetail(Long projectId, Long memberId) {
        Project project = getOwnedProject(projectId, memberId);

        List<ProjectNode> nodes = projectNodeRepository.findAllByProjectId(projectId);
        List<ProjectEdge> edges = projectEdgeRepository.findAllByProjectId(projectId);

        return ProjectConverter.toProjectDetailResDTO(project, nodes, edges);
    }
}
