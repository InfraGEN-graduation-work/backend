package com.infragen.infragen.domain.generation.generator.cloud;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.VolumeComponent;
import com.infragen.infragen.global.enums.ComponentType;

/** CLOUD_DEPLOY에서 애플리케이션과 선택된 의존 인프라의 Compose bootstrap을 생성한다. */
@Component
public class CloudComposeRenderer {
    private final List<CloudComposeServiceRenderer> serviceRenderers;

    public CloudComposeRenderer(List<CloudComposeServiceRenderer> serviceRenderers) {
        this.serviceRenderers = List.copyOf(serviceRenderers);
    }

    /**
     * 그래프에 존재하는 지원 dependency와 애플리케이션 연결 관계만 Compose에 반영한다.
     *
     * @param context CLOUD_DEPLOY renderer가 공유하는 파싱 결과
    * @return cloud Compose bootstrap 파일
     */
    public IaCFileDTO.FileContentResDTO render(CloudDeployContext context) {
        validateDependencyConfiguration(context);

        StringBuilder content = new StringBuilder("""
            # CLOUD_DEPLOY 부트스트랩입니다. 민감한 값은 외부 .env 파일에서 주입해 주세요.
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
        appendApplicationEnvironment(content, context);
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

    private void appendApplicationEnvironment(
        StringBuilder content,
        CloudDeployContext context
    ) {
        boolean hasMysql = context.hasIncomingDependency(ComponentType.MYSQL);
        boolean hasRedis = context.hasIncomingDependency(ComponentType.REDIS);
        if (!hasMysql && !hasRedis) {
            return;
        }

        content.append("    environment:\n");
        if (hasMysql) {
            content.append("      SPRING_DATASOURCE_URL: \"jdbc:mysql://mysql:3306/${MYSQL_DATABASE:?외부 .env에 설정 필요}\"\n")
                .append("      SPRING_DATASOURCE_USERNAME: \"${MYSQL_USER:?외부 .env에 설정 필요}\"\n")
                .append("      SPRING_DATASOURCE_PASSWORD: \"${MYSQL_PASSWORD:?외부 .env에 설정 필요}\"\n")
                .append("      MYSQL_HOST: mysql\n")
                .append("      MYSQL_PORT: \"3306\"\n");
        }
        if (hasRedis) {
            content.append("      REDIS_HOST: redis\n")
                .append("      REDIS_PORT: \"6379\"\n")
                .append("      REDIS_PASSWORD: \"${REDIS_PASSWORD:?외부 .env에 설정 필요}\"\n");
        }
    }
}
