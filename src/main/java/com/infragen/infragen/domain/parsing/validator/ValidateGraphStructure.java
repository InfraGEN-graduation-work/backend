package com.infragen.infragen.domain.parsing.validator;

import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;
import com.infragen.infragen.global.enums.ComponentType.ComponentCategory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.springframework.stereotype.Component;

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

        validateNginxConnection(nodeTypeMap, edges);
        validateNginxNames(nodes, nodeTypeMap);

        if (edges == null || edges.isEmpty()) {
            return;
        }

        Set<String> seenEdges = new HashSet<>();
        Map<String, Set<ComponentType>> applicationDependencyTypes = new HashMap<>();

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

            // 앱 하나에 같은 타입 의존이 둘이면 접속 변수(MYSQL_*, SPRING_DATASOURCE_* 등)가 서로 덮어써진다.
            if (targetType.getCategory() == ComponentCategory.APPLICATION
                    && !applicationDependencyTypes
                    .computeIfAbsent(target, key -> new HashSet<>())
                    .add(sourceType)) {
                throw new ParsingException(ParsingErrorCode.DUPLICATE_DEPENDENCY_TYPE);
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

    private void validateNginxConnection(Map<String, ComponentType> types, List<EdgeDTO> edges) {
        List<String> proxies = types.entrySet().stream()
            .filter(entry -> entry.getValue() == ComponentType.NGINX)
            .map(Map.Entry::getKey).toList();
        if (proxies.isEmpty()) {
            return;
        }
        long apps = types.values().stream()
            .filter(type -> type.getCategory() == ComponentCategory.APPLICATION).count();
        if (proxies.size() != 1 || apps != 1 || edges == null) {
            throw new ParsingException(ParsingErrorCode.INVALID_NGINX_CONNECTION);
        }
        String proxy = proxies.getFirst();
        boolean connected = false;
        for (EdgeDTO edge : edges) {
            if (edge == null) {
                continue;
            }
            if (proxy.equals(edge.getSourceNodeId())) {
                throw new ParsingException(ParsingErrorCode.INVALID_COMPONENT_DEPENDENCY);
            }
            if (proxy.equals(edge.getTargetNodeId())) {
                ComponentType source = types.get(edge.getSourceNodeId());
                if (source == null || source.getCategory() != ComponentCategory.APPLICATION) {
                    throw new ParsingException(ParsingErrorCode.INVALID_NGINX_CONNECTION);
                }
                connected = true;
            }
        }
        if (!connected) {
            throw new ParsingException(ParsingErrorCode.INVALID_NGINX_CONNECTION);
        }
    }

    private void validateNginxNames(List<NodeDTO> nodes, Map<String, ComponentType> types) {
        NodeDTO nginx = nodes.stream().filter(node -> node != null
            && types.get(node.getNodeId()) == ComponentType.NGINX).findFirst().orElse(null);
        if (nginx == null) {
            return;
        }
        String proxyName = containerName(nginx, ComponentType.NGINX);
        for (NodeDTO node : nodes) {
            if (node == null || node == nginx) {
                continue;
            }
            ComponentType type = types.get(node.getNodeId());
            if (type.getCategory() == ComponentCategory.APPLICATION) {
                continue;
            }
            String name = containerName(node, type);
            if (name.equals(proxyName) || name.equalsIgnoreCase("nginx")) {
                throw new ParsingException(ParsingErrorCode.INVALID_NGINX_PROPERTIES);
            }
        }
    }

    private String containerName(NodeDTO node, ComponentType type) {
        Object name = node.getProperties() == null ? null : node.getProperties().get("containerName");
        if (name instanceof String value && !value.isBlank()) {
            return value.trim();
        }
        return type == ComponentType.POSTGRESQL ? "postgres" : type.name().toLowerCase(java.util.Locale.ROOT);
    }
}
