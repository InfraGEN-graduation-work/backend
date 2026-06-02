package com.infragen.infragen.domain.project.converter;

import com.infragen.infragen.domain.project.dto.request.ProjectEdgeReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectEdgeResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.entity.ProjectEdge;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;

import java.util.List;
import java.util.Map;

public class ProjectEdgeConverter {
    public static ProjectEdgeResDTO.EdgeInfoResDTO toEdgeInfoResDTO(ProjectEdge edge) {
        return ProjectEdgeResDTO.EdgeInfoResDTO.builder()
            .id(edge.getId())
            .sourceNodeId(edge.getSourceNode().getId())
            .targetNodeId(edge.getTargetNode().getId())
            .build();
    }

    public static List<ProjectEdge> toEntityList(
        List<ProjectEdgeReqDTO.EdgeInfoReqDTO> edgeReqs,
        Project project,
        Map<String, ProjectNode> nodeMap
    ) {
        if (edgeReqs == null) {
            return List.of();
        }
        return edgeReqs.stream()
            .map(edgeReq -> {
                ProjectNode sourceNode = nodeMap.get(edgeReq.sourceNodeName());
                ProjectNode targetNode = nodeMap.get(edgeReq.targetNodeName());

                if (sourceNode == null || targetNode == null) {
                    throw new ProjectException(ProjectErrorCode.DEPENDENCY_MISSING_ERROR);
                }

                return ProjectEdge.builder()
                    .project(project)
                    .sourceNode(sourceNode)
                    .targetNode(targetNode)
                    .build();
            })
            .toList();
    }
}
