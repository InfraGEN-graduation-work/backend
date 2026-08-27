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
            .sourceNodeId(edge.getSourceNode().getNodeId())
            .targetNodeId(edge.getTargetNode().getNodeId())
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
                ProjectNode sourceNode = nodeMap.get(edgeReq.sourceNodeId());
                ProjectNode targetNode = nodeMap.get(edgeReq.targetNodeId());

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
