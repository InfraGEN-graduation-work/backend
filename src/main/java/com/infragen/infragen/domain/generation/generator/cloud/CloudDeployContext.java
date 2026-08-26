package com.infragen.infragen.domain.generation.generator.cloud;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;
import com.infragen.infragen.global.enums.ComponentType;
import com.infragen.infragen.global.enums.ComponentType.ComponentCategory;

/** CLOUD_DEPLOY renderer가 공유하는 파싱 결과의 실행 정보를 제공한다. */
public final class CloudDeployContext {
    private final SpringBootComponent application;
    private final List<BaseComponent> components;
    private final List<EdgeDTO> edges;

    private CloudDeployContext(
        SpringBootComponent application,
        List<BaseComponent> components,
        List<EdgeDTO> edges
    ) {
        this.application = application;
        this.components = List.copyOf(components);
        this.edges = edges == null ? List.of() : List.copyOf(edges);
    }

    /**
     * 파싱 결과에서 CLOUD_DEPLOY 산출물에 필요한 애플리케이션·dependency 정보를 만든다.
     *
     * @param parsingResult ParsingService가 반환한 그래프 결과
     * @return provider와 runtime renderer가 공유할 실행 정보
     * @throws IaCGenerationException 필수 애플리케이션이 없는 내부 계약 위반인 경우
     */
    public static CloudDeployContext from(ParsingResultDTO parsingResult) {
        if (parsingResult == null
            || parsingResult.getProjectId() == null
            || parsingResult.getComponents() == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }

        SpringBootComponent application = parsingResult.getComponents().stream()
            .filter(SpringBootComponent.class::isInstance)
            .map(SpringBootComponent.class::cast)
            .findFirst()
            .orElseThrow(() -> new IaCGenerationException(
                IaCGenerationErrorCode.INVALID_COMPONENT_STATE));

        return new CloudDeployContext(
            application,
            parsingResult.getComponents(),
            parsingResult.getEdges()
        );
    }

    public String javaVersion() {
        return application.getJavaVersion();
    }

    public int applicationPort() {
        return application.getPort();
    }

    public List<BaseComponent> dependencyComponents() {
        return components.stream()
            .filter(component -> component.getComponentType().getCategory() != ComponentCategory.APPLICATION)
            .toList();
    }

    public boolean hasComponent(ComponentType componentType) {
        return components.stream()
            .anyMatch(component -> component.getComponentType() == componentType);
    }

    public <T extends BaseComponent> T firstComponent(ComponentType componentType, Class<T> componentClass) {
        return components.stream()
            .filter(component -> component.getComponentType() == componentType)
            .filter(componentClass::isInstance)
            .map(componentClass::cast)
            .findFirst()
            .orElse(null);
    }

    public boolean hasIncomingDependency(ComponentType componentType) {
        Set<String> dependencyNodeIds = nodeIdsOf(componentType);
        if (application.getNodeId() == null || dependencyNodeIds.isEmpty()) {
            return false;
        }

        return edges.stream()
            .filter(edge -> edge != null)
            .anyMatch(edge -> application.getNodeId().equals(edge.getTargetNodeId())
                && dependencyNodeIds.contains(edge.getSourceNodeId()));
    }

    private Set<String> nodeIdsOf(ComponentType componentType) {
        Set<String> nodeIds = new HashSet<>();
        for (BaseComponent component : components) {
            if (component.getComponentType() == componentType) {
                nodeIds.add(component.getNodeId());
            }
        }
        return nodeIds;
    }
}
