package com.infragen.infragen.domain.project.service.query;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.project.converter.ProjectConverter;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectEdge;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.repository.projection.ProjectAccessPreview;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectQueryService {
    private final ProjectRepository projectRepository;
    private final ProjectNodeRepository projectNodeRepository;
    private final ProjectEdgeRepository projectEdgeRepository;
    private final ProjectAccessService projectAccessService;

    /** 인증된 회원이 소유하거나 참여하는 프로젝트와 각 접근 역할을 반환한다. */
    @Transactional(readOnly = true)
    public ProjectResDTO.ProjectPreviewListResDTO getProjects(Long memberId) {
        List<ProjectAccessPreview> projectList = projectRepository.findAllAccessibleByMemberId(memberId);

        return ProjectConverter.toProjectPreviewListResDTO(projectList);
    }

    // 소유권 검증 후 Project 반환 — Command·Query 공통
    @Transactional(readOnly = true)
    public Project getOwnedProject(Long projectId, Long memberId) {
        return projectRepository.findByIdAndMemberId(projectId, memberId)
            .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    /** 읽기 권한이 있는 회원에게 프로젝트와 저장된 graph를 반환한다. */
    @Transactional(readOnly = true)
    public ProjectResDTO.ProjectDetailResDTO getProjectDetail(Long projectId, Long memberId) {
        projectAccessService.requireReadAccess(projectId, memberId);
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));

        List<ProjectNode> nodes = projectNodeRepository.findAllByProjectId(projectId);
        List<ProjectEdge> edges = projectEdgeRepository.findAllByProjectId(projectId);

        return ProjectConverter.toProjectDetailResDTO(project, nodes, edges);
    }

    /**
     * owner 또는 EDITOR collaborator의 graph 수정용 프로젝트를 반환한다.
     *
     * @throws ProjectException 쓰기 권한이 없거나 프로젝트가 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public Project getWriteableProject(Long projectId, Long memberId) {
        projectAccessService.requireWriteAccess(projectId, memberId);

        return projectRepository.findById(projectId)
            .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }
}
