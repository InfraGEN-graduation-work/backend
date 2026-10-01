package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.global.enums.ComponentType.ComponentCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * 애플리케이션에 연결된 DATABASE 의존이 정확히 하나인지 확인한다.
     *
     * <p>DB별 contributor가 {@code SPRING_DATASOURCE_*} 생성 여부를 같은 기준으로 판단하도록 한곳에 둔다.
     * 같은 타입 DB 중복은 parsing 단계에서 이미 거부된다는 전제다.
     */
    public boolean hasSingleDatabaseDependency(String applicationNodeId) {
        long databaseCount = findIncomingDependencies(applicationNodeId).stream()
                .filter(dependency -> dependency.getComponentType().getCategory() == ComponentCategory.DATABASE)
                .count();
        return databaseCount == 1;
    }

    public BaseComponent getComponent(String nodeId) {
        return componentsByNodeId.get(nodeId);
    }

    public LinkedHashMap<String, String> getEnvVars() {
        return envVars;
    }
}
