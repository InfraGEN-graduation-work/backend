package com.infragen.infragen.domain.project.converter;

import com.infragen.infragen.domain.project.dto.request.ProjectNodeReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectNodeResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.enums.ComponentType;

import java.util.List;

public class ProjectNodeConverter {
    
    public static ProjectNodeResDTO.NodeInfoResDTO toNodeInfoResDTO(ProjectNode node) {
        return ProjectNodeResDTO.NodeInfoResDTO.builder()
            .id(node.getId())
            .nodeName(node.getNodeName())
            .componentType(node.getComponentType().name())
            .positionX(node.getPositionX())
            .positionY(node.getPositionY())
            .properties(node.getProperties())
            .build();
    }

    public static List<ProjectNode> toEntityList(
        List<ProjectNodeReqDTO.NodeInfoReqDTO> nodeReqs,
        Project project
    ) {
        if (nodeReqs == null) {
            return List.of();
        }
        return nodeReqs.stream()
            .map(nodeReq -> ProjectNode.builder()
                .nodeName(nodeReq.nodeName())
                .componentType(ComponentType.valueOf(nodeReq.componentType()))
                .positionX(nodeReq.positionX())
                .positionY(nodeReq.positionY())
                .properties(nodeReq.properties())
                .project(project)
                .build())
            .toList();
    }
}
