package com.infragen.infragen.domain.generation.generator.compose;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;

// Compose 생성 세션의 공유 상태 (파싱 결과 기반, 검증 재수행 없음)
public class ComposeGenerationContext {

    private final Map<String, BaseComponent> componentsByNodeId;
    private final List<EdgeDTO> edges;
    private final LinkedHashMap<String, String> envVars = new LinkedHashMap<>();

    public ComposeGenerationContext(ParsingResultDTO parsingResult) {
        this.componentsByNodeId = parsingResult.getComponents().stream()
            .collect(Collectors.toMap(
                BaseComponent::getNodeId,
                (component) -> component, // Function.identity() 대신 명시적 표현
                (left, right) -> left,
                LinkedHashMap::new
            ));
        this.edges = parsingResult.getEdges() != null
            ? List.copyOf(parsingResult.getEdges())
            : List.of();
    }

    // source -> target 방향의 edge에서 target으로 들어오는 모든 dependency
    public List<BaseComponent> findIncomingDependencies(String targetNodeId) {
        if (targetNodeId == null) {
            return List.of();
        }

        List<BaseComponent> dependencies = new ArrayList<>();
        for (EdgeDTO edge : edges) {
            if (edge == null || !targetNodeId.equals(edge.getTargetNodeId())) {
                continue;
            }

            BaseComponent source = componentsByNodeId.get(edge.getSourceNodeId());
            if (source == null) {
                continue;
            }

            dependencies.add(source);
        }
        return Collections.unmodifiableList(dependencies);
    }

    public BaseComponent getComponent(String nodeId) {
        return componentsByNodeId.get(nodeId);
    }

    public LinkedHashMap<String, String> getEnvVars() {
        return envVars;
    }
}
