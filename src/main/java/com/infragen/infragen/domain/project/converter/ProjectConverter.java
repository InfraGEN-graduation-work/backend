package com.infragen.infragen.domain.project.converter;

import java.util.List;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.dto.request.ProjectReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectNodeResDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectEdgeResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.entity.ProjectEdge;
import com.infragen.infragen.domain.project.enums.ProjectStatus;

public class ProjectConverter {
    public static Project toEntity(
        ProjectReqDTO.CreateProjectReqDTO request,
        Member member
    ) {
        return Project.builder()
            .title(request.title())
            .description(request.description())
            .status(ProjectStatus.DRAFT) // 최초 생성 시 상태는 초안
            .member(member)
            .build();
    }

    public static ProjectResDTO.CreateProjectResDTO toCreateProjectResDTO(
        Project project
    ) {
        return ProjectResDTO.CreateProjectResDTO.builder()
            .projectId(project.getId())
            .createdAt(project.getCreatedAt())
            .build();
    }

    // 조회용 converter 메서드
    public static ProjectResDTO.ProjectPreviewResDTO toProjectPreviewResDTO(Project project) {
        return ProjectResDTO.ProjectPreviewResDTO.builder()
            .projectId(project.getId())
            .title(project.getTitle())
            .description(project.getDescription())
            .status(project.getStatus().name())
            .createdAt(project.getCreatedAt())
            .build();
    }

    public static ProjectResDTO.ProjectPreviewListResDTO toProjectPreviewListResDTO(
        List<Project> projectList
    ) {
        List<ProjectResDTO.ProjectPreviewResDTO> previewList = projectList.stream()
            .map(ProjectConverter::toProjectPreviewResDTO).toList();

        return ProjectResDTO.ProjectPreviewListResDTO.builder()
            .projectList(previewList)
            .build();
    }

    public static ProjectResDTO.ProjectDetailResDTO toProjectDetailResDTO(
        Project project,
        List<ProjectNode> nodes,
        List<ProjectEdge> edges
    ) {
        List<ProjectNodeResDTO.NodeInfoResDTO> nodeDTOs = nodes.stream()
            .map(ProjectNodeConverter::toNodeInfoResDTO)
            .toList();

        List<ProjectEdgeResDTO.EdgeInfoResDTO> edgeDTOs = edges.stream()
            .map(ProjectEdgeConverter::toEdgeInfoResDTO)
            .toList();

        return ProjectResDTO.ProjectDetailResDTO.builder()
            .projectId(project.getId())
            .title(project.getTitle())
            .description(project.getDescription())
            .status(project.getStatus().name())
            .nodes(nodeDTOs)
            .edges(edgeDTOs)
            .build();
    }
}
