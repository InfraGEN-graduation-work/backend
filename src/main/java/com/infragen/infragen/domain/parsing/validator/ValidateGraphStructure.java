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
                throw new ParsingException(ParsingErrorCode.NULL_GRAPH_ELEMENT);
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
            validateInfrastructureTypeUniqueness(nodes, nodeTypeMap);
            validateWebServerPlacement(nodeTypeMap, List.of());
            return;
        }

        Set<String> seenEdges = new HashSet<>();
        Map<String, Set<ComponentType>> applicationDependencyTypes = new HashMap<>();

        for (EdgeDTO edge : edges) {
            if (edge == null) {
                throw new ParsingException(ParsingErrorCode.NULL_GRAPH_ELEMENT);
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

        // 앱 단위 중복(PARSING400_25)과 순환 같은 구조 오류가 먼저 걸리도록 마지막에 수행한다.
        validateInfrastructureTypeUniqueness(nodes, nodeTypeMap);
        validateWebServerPlacement(nodeTypeMap, edges);
    }

    // 웹 서버는 앱 앞단에서 앱 하나를 프록시하므로 노드 하나와 앱에서 오는 연결선 하나만 허용한다.
    // 그렇지 않으면 생성 단계에서 어느 앱을 프록시할지 정할 수 없고, 연결 없는 웹 서버는 산출물에 조용히 들어가거나 빠진다.
    // 방향(앱 → 웹 서버)은 위의 우선순위 검사가 이미 보장한다.
    private void validateWebServerPlacement(Map<String, ComponentType> nodeTypeMap, List<EdgeDTO> edges) {
        List<String> webServerNodeIds = nodeTypeMap.entrySet().stream()
                .filter(entry -> entry.getValue().getCategory() == ComponentCategory.WEB_SERVER)
                .map(Map.Entry::getKey)
                .toList();
        if (webServerNodeIds.isEmpty()) {
            return;
        }
        if (webServerNodeIds.size() > 1) {
            throw new ParsingException(ParsingErrorCode.DUPLICATE_WEB_SERVER_COMPONENT);
        }

        String webServerNodeId = webServerNodeIds.getFirst();
        // 같은 연결선이 중복돼도 하나로 센다. 위의 방향 검사와 같은 기준이다.
        Set<String> webServerEdgeKeys = new HashSet<>();
        EdgeDTO webServerEdge = null;
        for (EdgeDTO edge : edges) {
            if (webServerNodeId.equals(edge.getSourceNodeId()) || webServerNodeId.equals(edge.getTargetNodeId())) {
                if (webServerEdgeKeys.add(edge.getSourceNodeId() + "->" + edge.getTargetNodeId())) {
                    webServerEdge = edge;
                }
            }
        }

        boolean fromApplication = webServerEdgeKeys.size() == 1
                && webServerNodeId.equals(webServerEdge.getTargetNodeId())
                && nodeTypeMap.get(webServerEdge.getSourceNodeId()).getCategory() == ComponentCategory.APPLICATION;
        if (!fromApplication) {
            throw new ParsingException(ParsingErrorCode.INVALID_WEB_SERVER_CONNECTION);
        }
    }

    // LOCAL_DEV .env는 타입별 고정 변수(MYSQL_* 등)를 쓰므로 같은 타입 노드가 둘이면 마지막 노드 값만 남는다.
    // 앱에 연결되지 않은 노드도 컨테이너와 .env에 등록되므로 연결 여부와 무관하게 그래프 전체에서 센다.
    private void validateInfrastructureTypeUniqueness(List<NodeDTO> nodes, Map<String, ComponentType> nodeTypeMap) {
        Set<ComponentType> seenTypes = new HashSet<>();
        for (NodeDTO node : nodes) {
            ComponentType type = nodeTypeMap.get(node.getNodeId());
            boolean infrastructure = type.getCategory() == ComponentCategory.DATABASE
                    || type.getCategory() == ComponentCategory.CACHE;
            if (infrastructure && !seenTypes.add(type)) {
                throw new ParsingException(ParsingErrorCode.DUPLICATE_COMPONENT_TYPE);
            }
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
