package com.infragen.infragen.domain.generation.generator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.generation.generator.compose.ComposeGenerationContext;
import com.infragen.infragen.domain.generation.generator.compose.ComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.compose.ComposeYamlSupport;
import com.infragen.infragen.domain.generation.generator.compose.HostAppEnvContributor;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.global.enums.ComponentType;
import com.infragen.infragen.global.enums.ComponentType.ComponentCategory;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DockerComposeIaCGenerator implements IaCGenerator {
    private final Map<ComponentType, ComposeServiceRenderer> rendererMap;
    // LOCAL_DEV — 호스트에서 실행할 애플리케이션용 .env 키와 값을 context에 추가
    private final Map<ComponentType, HostAppEnvContributor> hostAppEnvContributorMap;

    // Compose renderer와 호스트 앱 env contributor를 주입받아 Map으로 보관
    public DockerComposeIaCGenerator(
        List<ComposeServiceRenderer> renderers,
        List<HostAppEnvContributor> hostAppEnvContributors
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
        }

        // LOCAL_DEV — 호스트에서 실행할 애플리케이션용 .env 키와 값을 context에 추가
        contributeHostAppEnv(sortedComponents, context);

        // 렌더링된 결과를 조립하여 Docker Compose 파일을 생성
        String dockerComposeContent = assembleDockerCompose(serviceBlocks);
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
                    .fileName("docker-compose.yml")
                    .content(dockerComposeContent)
                    .build(),
                IaCFileDTO.FileContentResDTO.builder()
                    .fileName(".env")
                    .content(envContent)
                    .build()
            ))
            .build();
    }

    // LOCAL_DEV — 애플리케이션별 incoming DB 의존에 대해 HostAppEnvContributor 호출
    private void contributeHostAppEnv(List<BaseComponent> components, ComposeGenerationContext context) {
        for (BaseComponent component : components) {
            if (component.getComponentType().getCategory() != ComponentCategory.APPLICATION) {
                continue;
            }

            List<BaseComponent> databases = context.findIncomingDependencies(
                component.getNodeId(), ComponentCategory.DATABASE);

            for (BaseComponent database : databases) {
                HostAppEnvContributor contributor = hostAppEnvContributorMap.get(database.getComponentType());
                if (contributor == null) {
                    log.warn(
                        "HostAppEnvContributor 없음: dbType={}, appNodeId={}",
                        database.getComponentType(),
                        component.getNodeId()
                    );
                    continue;
                }
                contributor.contributeHostAppEnv(database, component, context);
            }
        }
    }

    private String assembleDockerCompose(List<String> serviceBlocks) {
        if (serviceBlocks.isEmpty()) {
            return "# 노드가 할당되지 않았습니다.\n";
        }

        StringBuilder content = new StringBuilder();
        content.append("services:\n");
        for (String block : serviceBlocks) {
            content.append(block);
        }

        if (content.charAt(content.length() - 1) == '\n') {
            content.setLength(content.length() - 1);
        }

        content.append('\n');
        return content.toString();
    }
}
