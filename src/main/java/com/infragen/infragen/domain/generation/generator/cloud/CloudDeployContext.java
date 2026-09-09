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

    /** @return runtime Dockerfile에 사용할 Java major version */
    public String javaVersion() {
        return application.getJavaVersion();
    }

    /** @return Cloud runtime에 노출할 Spring Boot 애플리케이션 포트 */
    public int applicationPort() {
        return application.getPort();
    }

    /** @return 선택된 애플리케이션으로 연결된 non-application component 목록 */
    public List<BaseComponent> dependencyComponents() {
        Set<String> incomingDependencyNodeIds = incomingDependencyNodeIds();
        return components.stream()
            .filter(component -> component.getComponentType().getCategory() != ComponentCategory.APPLICATION)
            .filter(component -> incomingDependencyNodeIds.contains(component.getNodeId()))
            .toList();
    }

    /**
     * 연결된 dependency 중 지정한 component type과 DTO 타입에 해당하는 항목을 찾는다.
     *
     * @param componentType 찾을 component type
     * @param componentClass 반환할 DTO 타입
     * @param <T> 반환 component 타입
     * @return 일치하는 연결 dependency 또는 없으면 null
     */
    public <T extends BaseComponent> T dependencyComponent(
        ComponentType componentType,
        Class<T> componentClass
    ) {
        return dependencyComponents().stream()
            .filter(component -> component.getComponentType() == componentType)
            .filter(componentClass::isInstance)
            .map(componentClass::cast)
            .findFirst()
            .orElse(null);
    }

    /**
     * 선택된 애플리케이션으로 연결된 dependency 중 지정한 type의 존재 여부를 확인한다.
     *
     * @param componentType 확인할 dependency type
     * @return incoming edge로 연결된 dependency가 있는지 여부
     */
    public boolean hasIncomingDependency(ComponentType componentType) {
        return dependencyComponents().stream()
            .anyMatch(component -> component.getComponentType() == componentType);
    }

    private Set<String> incomingDependencyNodeIds() {
        if (application.getNodeId() == null) {
            return Set.of();
        }

        Set<String> nodeIds = new HashSet<>();
        for (EdgeDTO edge : edges) {
            if (edge != null && application.getNodeId().equals(edge.getTargetNodeId())) {
                nodeIds.add(edge.getSourceNodeId());
            }
        }
        return nodeIds;
    }
}
