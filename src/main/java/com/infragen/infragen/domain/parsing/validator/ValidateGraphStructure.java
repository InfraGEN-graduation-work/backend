package com.infragen.infragen.domain.parsing.validator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

@Component
public class ValidateGraphStructure {

    public void validate(List<NodeDTO> nodes, List<EdgeDTO> edges) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        Map<String, ComponentType> nodeTypeMap = new HashMap<>();
        Map<String, List<String>> adjList = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();

        for (NodeDTO node : nodes) {
            if (node == null) {
                continue;
            }

            String nodeId = node.getNodeId();
            if (nodeId == null || nodeId.isBlank()) {
                throw new ParsingException(ParsingErrorCode.MISSING_NODE_ID);
            }
            if (nodeTypeMap.containsKey(nodeId)) {
                throw new ParsingException(ParsingErrorCode.DUPLICATE_NODE_ID);
            }

            ComponentType type = resolveComponentType(node.getComponentType());

            nodeTypeMap.put(nodeId, type);
            adjList.put(nodeId, new ArrayList<>());
            indegree.put(nodeId, 0);
        }

        if (edges == null || edges.isEmpty()) {
            return;
        }

        Set<String> seenEdges = new HashSet<>();

        for (EdgeDTO edge : edges) {
            if (edge == null) {
                continue;
            }

            // source 노드와 target 노드 추출
            String source = edge.getSourceNodeId();
            String target = edge.getTargetNodeId();

            if (source == null || target == null || source.isBlank() || target.isBlank()) {
                throw new ParsingException(ParsingErrorCode.INVALID_EDGE_ENDPOINT);
            }

            if (!adjList.containsKey(source) || !adjList.containsKey(target)) {
                throw new ParsingException(ParsingErrorCode.INVALID_EDGE_NODE);
            }

            // 중복 edge 검증
            String edgeKey = source + "->" + target;
            if (!seenEdges.add(edgeKey)) {
                continue;
            }

            ComponentType sourceType = nodeTypeMap.get(source);
            ComponentType targetType = nodeTypeMap.get(target);

            if (sourceType.getStartupPriority() > targetType.getStartupPriority()) {
                throw new ParsingException(ParsingErrorCode.INVALID_COMPONENT_DEPENDENCY);
            }

            adjList.get(source).add(target);
            // target 노드의 진입 차수 증가
            indegree.put(target, indegree.get(target) + 1);
        }

        Queue<String> queue = new ArrayDeque<>();

        // 큐 초기화
        for (String nodeId : indegree.keySet()) {
            if (indegree.get(nodeId) == 0) {
                queue.add(nodeId);
            }
        }

        int visitedCount = 0;
        
        // 위상 정렬을 위한 큐 처리
        while (!queue.isEmpty()) {
            String current = queue.poll();
            visitedCount++;

            for (String neighbor : adjList.get(current)) {
                indegree.put(neighbor, indegree.get(neighbor) - 1);
                if (indegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (visitedCount != nodeTypeMap.size()) {
            throw new ParsingException(ParsingErrorCode.CYCLE_DETECTED);
        }
    }

    private ComponentType resolveComponentType(String componentType) {
        if (componentType == null || componentType.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_COMPONENT_TYPE);
        }
        try {
            return ComponentType.valueOf(componentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ParsingException(ParsingErrorCode.UNSUPPORTED_COMPONENT_TYPE);
        }
    }
}
