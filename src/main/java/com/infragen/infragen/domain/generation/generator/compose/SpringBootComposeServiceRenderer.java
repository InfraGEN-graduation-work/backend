package com.infragen.infragen.domain.generation.generator.compose;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;
import com.infragen.infragen.global.enums.ComponentType;
import com.infragen.infragen.global.enums.ComponentType.ComponentCategory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SpringBootComposeServiceRenderer implements ComposeServiceRenderer {

    private static final String TYPE_LABEL = "Spring Boot";
    private static final String DEFAULT_JAVA_VERSION = "17";

    private final Map<ComponentType, DatabaseEnvContributor> contributorMap;

    public SpringBootComposeServiceRenderer(List<DatabaseEnvContributor> contributors) {
        this.contributorMap = contributors.stream()
            .collect(Collectors.toMap(DatabaseEnvContributor::getDatabaseType, contributor -> contributor));
    }

    @Override
    public ComponentType getSupportedType() {
        return ComponentType.SPRING_BOOT;
    }

    @Override
    public String render(BaseComponent component, ComposeGenerationContext context) {
        SpringBootComponent spring = (SpringBootComponent) component;

        String serviceName = ComposeYamlSupport.toServiceName(
            spring.getContainerName(), spring.getName(), TYPE_LABEL);
        String containerName = resolveContainerName(spring.getContainerName(), serviceName);
        String javaVersion = resolveJavaVersion(spring.getJavaVersion());
        int port = spring.getPort();

        // 애플리케이션이 의존하는 모든 데이터베이스 컴포넌트를 찾음
        List<BaseComponent> databases = context.findIncomingDependencies(
            spring.getNodeId(), ComponentCategory.DATABASE);
        List<String> envKeys = new ArrayList<>();

        StringBuilder yaml = new StringBuilder();
        yaml.append("  ").append(serviceName).append(":\n");
        yaml.append("    image: eclipse-temurin:").append(javaVersion).append("-jdk\n");
        yaml.append("    container_name: ").append(containerName).append('\n');
        yaml.append("    ports:\n");
        yaml.append("      - \"").append(port).append(':').append(port).append("\"\n");

        if (!databases.isEmpty()) {
            yaml.append("    depends_on:\n");
            for (BaseComponent database : databases) {
                yaml.append("      - ").append(resolveDependencyServiceName(database)).append('\n');

                DatabaseEnvContributor contributor = contributorMap.get(database.getComponentType());
                if (contributor == null) {
                    log.warn(
                        "DatabaseEnvContributor 없음: dbType={}, appNodeId={}",
                        database.getComponentType(),
                        spring.getNodeId()
                    );
                    continue;
                }
                contributor.contributeConnection(database, spring, context, envKeys);
            }
        }

        if (!envKeys.isEmpty()) {
            yaml.append("    env_file:\n");
            yaml.append("      - .env\n");
            yaml.append("    environment:\n");
            for (String key : envKeys) {
                yaml.append("      ").append(key).append(": ${").append(key).append("}\n");
            }
        }

        return yaml.toString();
    }

    private static String resolveContainerName(String containerName, String serviceName) {
        if (containerName != null && !containerName.isBlank()) {
            return containerName.trim();
        }
        return serviceName;
    }

    private static String resolveJavaVersion(String javaVersion) {
        if (javaVersion != null && !javaVersion.isBlank()) {
            return javaVersion.trim();
        }
        return DEFAULT_JAVA_VERSION;
    }

    // depends_on 키는 해당 DB renderer의 toServiceName 규칙과 동일해야 함
    private static String resolveDependencyServiceName(BaseComponent database) {
        if (database instanceof MySQLComponent mysql) {
            return ComposeYamlSupport.toServiceName(mysql.getContainerName(), null, "MySQL");
        }
        return ComposeYamlSupport.toServiceName(null, null, database.getComponentType().name());
    }
}
