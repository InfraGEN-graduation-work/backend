package com.infragen.infragen.domain.project.converter;

import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.project.entity.ProjectEdge;
import com.infragen.infragen.domain.project.entity.ProjectNode;

import java.util.List;

public final class ProjectGraphParsingConverter {
    private ProjectGraphParsingConverter() {
    }

    /**
     * 저장된 project graph를 ParsingService 입력 DTO로 변환한다.
     *
     * @param nodes 저장된 project node 목록
     * @param edges 저장된 project edge 목록
     * @return parsing에 사용할 graph request
     */
    public static ParsingReqDTO toParsingReqDTO(
            List<ProjectNode> nodes,
            List<ProjectEdge> edges
    ) {
        ParsingReqDTO request = new ParsingReqDTO();
        request.setNodes(nodes.stream()
                .map(ProjectGraphParsingConverter::toNodeDTO)
                .toList());
        request.setEdges(edges.stream()
                .map(ProjectGraphParsingConverter::toEdgeDTO)
                .toList());
        return request;
    }

    private static NodeDTO toNodeDTO(ProjectNode node) {
        return new NodeDTO(
                node.getNodeId(),
                node.getComponentType().name(),
                node.getPositionX() == null ? null : node.getPositionX().floatValue(),
                node.getPositionY() == null ? null : node.getPositionY().floatValue(),
                node.getProperties()
        );
    }

    private static EdgeDTO toEdgeDTO(ProjectEdge edge) {
        EdgeDTO result = new EdgeDTO();
        result.setEdgeId(edge.getId() == null ? null : edge.getId().toString());
        result.setSourceNodeId(edge.getSourceNode().getNodeId());
        result.setTargetNodeId(edge.getTargetNode().getNodeId());
        return result;
    }
}
