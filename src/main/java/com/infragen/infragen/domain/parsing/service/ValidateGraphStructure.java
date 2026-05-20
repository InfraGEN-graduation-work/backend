package com.infragen.infragen.domain.parsing.service;

import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ValidateGraphStructure {
    private static final Map<String, Integer> COMPONENT_LEVELS = new HashMap<>();
    static {
        COMPONENT_LEVELS.put("GITHUB_ACTIONS", 1);
        COMPONENT_LEVELS.put("OCI", 2);
        COMPONENT_LEVELS.put("DOCKER", 2);
        COMPONENT_LEVELS.put("MYSQL", 3);
        COMPONENT_LEVELS.put("REDIS", 3);
        COMPONENT_LEVELS.put("ORACLE", 3);
        COMPONENT_LEVELS.put("KAFKA", 4);
        COMPONENT_LEVELS.put("SPRING_BOOT", 5);
        COMPONENT_LEVELS.put("NGINX", 6);
    }
    public void validate(List<NodeDTO> nodes , List<EdgeDTO> edges) {
        if (nodes == null || nodes.isEmpty()) return;

        Map<String, List<String>> adjList = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, String> nodeTypeMap = new HashMap<>();
        for (NodeDTO node : nodes) {
            if (node == null || node.getNodeId() == null) continue;
            adjList.put(node.getNodeId(), new ArrayList<>());
            indegree.put(node.getNodeId(), 0);
            nodeTypeMap.put(node.getNodeId(), node.getComponentType());
        }

        if (edges == null || edges.isEmpty()) return;

        for (EdgeDTO edge : edges) {
            if (edge == null) continue;

            String source = edge.getSourceNodeId();
            String target = edge.getTargetNodeId();

            if (!adjList.containsKey(source) || !adjList.containsKey(target)) {
                throw new ParsingException("존재하지 않는 노드가 연결선에 포함되어 있습니다.");
            }

            String sourceType = nodeTypeMap.get(source);
            String targetType = nodeTypeMap.get(target);
            int sourceLevel = COMPONENT_LEVELS.getOrDefault(sourceType, 99);
            int targetLevel = COMPONENT_LEVELS.getOrDefault(targetType, 99);


            if (sourceLevel == 99) {
                throw new ParsingException("정의되지 않은 컴포넌트 타입이 포함되어 있습니다."+sourceType);
            }
            if (targetLevel == 99) {
                throw new ParsingException("정의되지 않은 컴포넌트 타입이 포함되어 있습니다."+targetType);
            }
            if (sourceLevel > targetLevel) {
                throw new ParsingException("잘못된 의존성 방향입니다.");
            }
            adjList.get(source).add(target);
            indegree.put(target, indegree.get(target) + 1);
        }

        Queue<String> queue = new LinkedList<>();
        for (String nodeId : indegree.keySet()) {
            if (indegree.get(nodeId) == 0) {
                queue.add(nodeId);
            }
        }

        int visitedCount = 0;

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

        if (visitedCount != nodes.size()) {
            throw new ParsingException("인프라 아키텍처에 순환 참조가 존재합니다.");
        }
    }
}
