package com.infragen.infragen.domain.generation.generator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.generation.generator.application.ApplicationEnvMapper;
import com.infragen.infragen.domain.generation.generator.compose.ComposeGenerationContext;
import com.infragen.infragen.domain.generation.generator.compose.ComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.compose.ComposeYamlSupport;
import com.infragen.infragen.domain.generation.generator.compose.HostAppEnvContributor;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.VolumeComponent;
import com.infragen.infragen.global.enums.ComponentType;
import com.infragen.infragen.global.enums.ComponentType.ComponentCategory;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DockerComposeIaCGenerator implements LocalIaCGenerator {
    private final Map<ComponentType, ComposeServiceRenderer> rendererMap;
    // LOCAL_DEV — 호스트에서 실행할 애플리케이션용 .env 키와 값을 context에 추가
    private final Map<ComponentType, HostAppEnvContributor> hostAppEnvContributorMap;
    // 앱 타입별 프레임워크 변수 규칙
    private final Map<ComponentType, ApplicationEnvMapper> applicationEnvMapperMap;

    // Compose renderer, 호스트 앱 env contributor, 앱 env mapper를 주입받아 Map으로 보관
    public DockerComposeIaCGenerator(
        @NonNull List<ComposeServiceRenderer> renderers,
        @NonNull List<HostAppEnvContributor> hostAppEnvContributors,
        @NonNull List<ApplicationEnvMapper> applicationEnvMappers
    ) {
        this.rendererMap = renderers.stream()
            .collect(Collectors.toMap(
                ComposeServiceRenderer::getSupportedType,
                renderer -> renderer,
                (existing, replacement) -> existing
            ));
        this.hostAppEnvContributorMap = hostAppEnvContributors.stream()
            .collect(Collectors.toMap(
                HostAppEnvContributor::getDependencyType,
                contributor -> contributor,
                (existing, replacement) -> existing
            ));
        this.applicationEnvMapperMap = applicationEnvMappers.stream()
            .collect(Collectors.toMap(
                ApplicationEnvMapper::getApplicationType,
                mapper -> mapper,
                (existing, replacement) -> existing
            ));
    }

    // Docker Compose 파일을 생성하기 위한 OutputFormat을 반환
    @Override
    public OutputFormat getOutputFormat() {
        return OutputFormat.DOCKER_COMPOSE;
    }

    // 모든 컴포넌트를 순회하며 Renderer를 찾아서 렌더링하고, 렌더링된 결과를 조립하여 Docker Compose 파일을 생성
    @Override
    public IaCFileDTO.BundleResDTO generate(ParsingResultDTO parsingResult) {
        log.debug("Docker Compose 생성 요청: projectId={}", parsingResult.getProjectId());

        // 렌더러 Map에서 지원하는 컴포넌트 타입을 찾음
        ComposeGenerationContext context = new ComposeGenerationContext(parsingResult);
        List<String> serviceBlocks = new ArrayList<>();
        Set<String> rootVolumeNames = new LinkedHashSet<>();
        /**
         * 컴포넌트를 시작 우선순위 순으로 정렬
         * 의존 관계를 고려하여 컴포넌트를 정렬하기 위함
        */
        List<BaseComponent> sortedComponents = parsingResult.getComponents().stream()
            .sorted(Comparator.comparingInt(component -> component.getComponentType().getStartupPriority()))
            .toList();

        // 의존 인프라만 compose services: 렌더 (APPLICATION은 호스트 실행 — compose에 포함되지 않음)
        for (BaseComponent component : sortedComponents) {
            if (component.getComponentType().getCategory() == ComponentCategory.APPLICATION) {
                continue;
            }

            ComposeServiceRenderer renderer = rendererMap.get(component.getComponentType());
            if (renderer == null) {
                log.warn(
                    "ComposeServiceRenderer 없음: type={}, nodeId={}",
                    component.getComponentType(),
                    component.getNodeId()
                );
                continue;
            }
            serviceBlocks.add(renderer.render(component, context));
            if (component instanceof VolumeComponent volumeComponent
                && volumeComponent.getVolumeName() != null
                && !volumeComponent.getVolumeName().isBlank()) {
                rootVolumeNames.add(volumeComponent.getVolumeName().trim());
            }
        }

        // LOCAL_DEV — 호스트에서 실행할 애플리케이션용 .env 키와 값을 context에 추가
        contributeHostAppEnv(sortedComponents, context);

        // 렌더링된 결과를 조립하여 Docker Compose 파일을 생성
        String dockerComposeContent = assembleDockerCompose(
            serviceBlocks,
            rootVolumeNames,
            findMultipleDatabaseNotice(sortedComponents, context)
        );
        // .env 파일을 생성
        String envContent = ComposeYamlSupport.formatEnvFile(context.getEnvVars());

        log.debug(
            "Docker Compose 생성 완료: projectId={}, services={}, envKeys={}",
            parsingResult.getProjectId(),
            serviceBlocks.size(),
            context.getEnvVars().size()
        );


        return IaCFileDTO.BundleResDTO.builder()
            .files(List.of(
                IaCFileDTO.FileContentResDTO.builder()
                    .fileName("local/docker-compose.yml")
                    .content(dockerComposeContent)
                    .build(),
                IaCFileDTO.FileContentResDTO.builder()
                    .fileName("local/.env")
                    .content(envContent)
                    .build()
            ))
            .build();
    }

    // LOCAL_DEV — 애플리케이션별 incoming dependency의 접속 정보를 모아 .env에 앱 타입별 매핑과 함께 넣는다.
    private void contributeHostAppEnv(List<BaseComponent> components, ComposeGenerationContext context) {
        for (BaseComponent component : components) {
            if (component.getComponentType().getCategory() != ComponentCategory.APPLICATION) {
                continue;
            }

            List<BaseComponent> dependencies = context.findIncomingDependencies(component.getNodeId());
            ApplicationEnvMapper mapper = applicationEnvMapperMap.get(component.getComponentType());
            if (mapper == null) {
                log.warn(
                    "ApplicationEnvMapper 없음: type={}, nodeId={}",
                    component.getComponentType(),
                    component.getNodeId()
                );
            }
            // DB가 둘 이상이면 기본 DataSource를 정할 수 없어 앱 프레임워크 변수 없이 타입별 접속 변수만 넣는다.
            boolean singleDatabase = context.hasSingleDatabaseDependency(component.getNodeId());

            for (BaseComponent dependency : dependencies) {
                HostAppEnvContributor contributor = hostAppEnvContributorMap.get(
                    dependency.getComponentType());
                if (contributor == null) {
                    log.warn(
                        "HostAppEnvContributor 없음: dependencyType={}, appNodeId={}",
                        dependency.getComponentType(),
                        component.getNodeId()
                    );
                    continue;
                }
                if (singleDatabase && mapper != null) {
                    contributor.jdbcConnection(dependency).ifPresent(connection ->
                        context.getEnvVars().putAll(mapper.datasourceEnvironment(
                            connection.url(), connection.username(), connection.password())));
                }
                context.getEnvVars().putAll(contributor.hostAppEnvironment(dependency));
            }
        }
    }

    // DB가 둘 이상인 앱이 하나라도 있으면 DataSource 변수가 빠진 이유를 Compose에 안내한다.
    private Optional<String> findMultipleDatabaseNotice(
        List<BaseComponent> components,
        ComposeGenerationContext context
    ) {
        return components.stream()
            .filter(component -> component.getComponentType().getCategory() == ComponentCategory.APPLICATION)
            .filter(component -> context.hasMultipleDatabaseDependencies(component.getNodeId()))
            .map(component -> applicationEnvMapperMap.get(component.getComponentType()))
            .filter(Objects::nonNull)
            .map(mapper -> mapper.multipleDatabaseNotice(".env"))
            .findFirst();
    }

    private String assembleDockerCompose(
        List<String> serviceBlocks,
        Set<String> rootVolumeNames,
        Optional<String> multipleDatabaseNotice
    ) {
        if (serviceBlocks.isEmpty()) {
            return "# 노드가 할당되지 않았습니다.\n";
        }

        StringBuilder content = new StringBuilder();
        multipleDatabaseNotice.ifPresent(content::append);
        content.append("services:\n");
        for (String block : serviceBlocks) {
            content.append(block);
        }
        if (!rootVolumeNames.isEmpty()) {
            content.append("\nvolumes:\n");
            for (String volumeName : rootVolumeNames) {
                content.append("  ").append(volumeName).append(":\n");
            }
        }

        return content.toString();
    }
}
