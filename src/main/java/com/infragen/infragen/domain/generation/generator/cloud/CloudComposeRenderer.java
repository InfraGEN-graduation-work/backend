package com.infragen.infragen.domain.generation.generator.cloud;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.generator.application.ApplicationEnvMapper;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.VolumeComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** CLOUD_DEPLOY에서 애플리케이션과 선택된 의존 인프라의 Compose bootstrap을 생성한다. */
@Component
public class CloudComposeRenderer {
    private static final String ENV_SOURCE = "app environment";

    private final List<CloudComposeServiceRenderer> serviceRenderers;
    private final Map<ComponentType, ApplicationEnvMapper> applicationEnvMapperMap;

    /**
     * 서비스 블록, {@code depends_on}, 앱 환경변수 출력 순서를 {@code ComponentType} 선언 순서로 고정한다.
     * Spring 주입 순서에 따라 산출물이 달라지지 않게 하기 위해서다.
     */
    public CloudComposeRenderer(
        List<CloudComposeServiceRenderer> serviceRenderers,
        List<ApplicationEnvMapper> applicationEnvMappers
    ) {
        this.serviceRenderers = serviceRenderers.stream()
            .sorted(Comparator.comparing(CloudComposeServiceRenderer::getSupportedType))
            .toList();
        this.applicationEnvMapperMap = applicationEnvMappers.stream()
            .collect(Collectors.toMap(
                ApplicationEnvMapper::getApplicationType,
                mapper -> mapper,
                (existing, replacement) -> existing
            ));
    }

    /**
     * 그래프에 존재하는 지원 dependency와 애플리케이션 연결 관계만 Compose에 반영한다.
     *
     * @param context CLOUD_DEPLOY renderer가 공유하는 파싱 결과
    * @return cloud Compose bootstrap 파일
     */
    public IaCFileDTO.FileContentResDTO render(CloudDeployContext context) {
        validateDependencyConfiguration(context);
        ApplicationEnvMapper mapper = applicationEnvMapper(context);

        StringBuilder content = new StringBuilder("""
            # CLOUD_DEPLOY 부트스트랩입니다. 민감한 값은 외부 .env 파일에서 주입해 주세요.
            """);
        if (context.hasMultipleDatabaseDependencies()) {
            content.append(mapper.multipleDatabaseNotice(ENV_SOURCE));
        }
        content.append("""
            services:
              app:
                build:
                  context: .
                  dockerfile: Dockerfile
                image: infragen-runtime:plan-only
                ports:
            """);
        content.append(String.format(
            "      - \"${APP_PORT:-%d}:%d\"%n",
            context.applicationPort(),
            context.applicationPort()
        ));
        appendApplicationEnvironment(content, context, mapper);
        content.append("    env_file:\n");
        content.append("      - .env\n");

        List<String> dependencies = new ArrayList<>();
        List<String> serviceBlocks = new ArrayList<>();
        for (CloudComposeServiceRenderer renderer : serviceRenderers) {
            if (!renderer.isEnabled(context)) {
                continue;
            }
            serviceBlocks.add(renderer.render(context));
            if (renderer.isDependency(context)) {
                dependencies.add(renderer.getServiceName());
            }
        }

        if (!dependencies.isEmpty()) {
            content.append("    depends_on:\n");
            for (String dependency : dependencies) {
                content.append("      - ").append(dependency).append('\n');
            }
        }

        for (String serviceBlock : serviceBlocks) {
            content.append(serviceBlock);
        }
        appendRootVolumes(content, context);

        return IaCFileDTO.FileContentResDTO.builder()
            .fileName("docker-compose.cloud.yml")
            .content(content.toString())
            .build();
    }

    private void appendRootVolumes(StringBuilder content, CloudDeployContext context) {
        Set<String> volumeNames = new LinkedHashSet<>();

        // Compose에서 의존 인프라의 볼륨 이름을 수집한다.
        for (BaseComponent dependency : context.dependencyComponents()) {
            if (!(dependency instanceof VolumeComponent volumeComponent)) {
                continue;
            }

            String volumeName = volumeComponent.getVolumeName();

            // Compose에서 volume 이름이 비어있으면 오류가 발생하므로, 비어있지 않은 경우에만 추가한다.
            if (volumeName != null && !volumeName.isBlank()) {
                volumeNames.add(volumeName.trim());
            }
        }
        if (volumeNames.isEmpty()) {
            return;
        }

        content.append("\nvolumes:\n");

        for (String volumeName : volumeNames) {
            content.append("  ").append(volumeName).append(":\n");
        }
    }

    // 의존 인프라가 중복으로 존재하는 경우, Compose에서 어떤 의존 인프라를 선택해야 하는지 모호해지므로 오류를 발생시킨다.
    private void validateDependencyConfiguration(CloudDeployContext context) {
        for (CloudComposeServiceRenderer renderer : serviceRenderers) {
            long matchingDependencyCount = context.dependencyComponents().stream()
                .filter(component -> component.getComponentType() == renderer.getSupportedType())
                .count();

            if (matchingDependencyCount > 1) {
                throw new IaCGenerationException(
                    IaCGenerationErrorCode.AMBIGUOUS_DEPENDENCY_CONFIGURATION);
            }
        }
    }

    // 앱 타입의 매퍼가 없으면 DB 접속 변수가 조용히 빠지므로 내부 계약 위반으로 거부한다.
    private ApplicationEnvMapper applicationEnvMapper(CloudDeployContext context) {
        ApplicationEnvMapper mapper = applicationEnvMapperMap.get(context.applicationType());
        if (mapper == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }
        return mapper;
    }

    private void appendApplicationEnvironment(
        StringBuilder content,
        CloudDeployContext context,
        ApplicationEnvMapper mapper
    ) {
        // DB가 둘 이상이면 기본 DataSource를 정할 수 없어 앱 프레임워크 변수 없이 타입별 접속 변수만 넣는다.
        boolean singleDatabase = context.hasSingleDatabaseDependency();

        StringBuilder environment = new StringBuilder();
        for (CloudComposeServiceRenderer renderer : serviceRenderers) {
            if (!renderer.isEnabled(context)) {
                continue;
            }
            if (singleDatabase) {
                renderer.jdbcConnection().ifPresent(connection -> mapper
                    .datasourceEnvironment(connection.url(), connection.username(), connection.password())
                    .forEach((name, value) -> environment
                        .append("      ").append(name).append(": ").append(value).append('\n')));
            }
            renderer.applicationEnvironment().forEach((name, value) -> environment
                .append("      ").append(name).append(": ").append(value).append('\n'));
        }
        if (environment.isEmpty()) {
            return;
        }

        content.append("    environment:\n").append(environment);
    }
}
