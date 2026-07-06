package com.infragen.infragen.domain.generation.generator.compose;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLEnvComponent;
import com.infragen.infragen.global.enums.ComponentType;

@Component
public class MysqlComposeServiceRenderer implements ComposeServiceRenderer {

    private static final String TYPE_LABEL = "MySQL";
    private static final String DEFAULT_IMAGE = "mysql:8.0";

    @Override
    public ComponentType getSupportedType() {
        return ComponentType.MYSQL;
    }

    @Override
    public String render(BaseComponent component, ComposeGenerationContext context) {
        MySQLComponent mysql = (MySQLComponent) component;
        MySQLEnvComponent env = mysql.getEnv();
        if (env == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }

        String serviceName = ComposeYamlSupport.toServiceName(
            mysql.getContainerName(), null, TYPE_LABEL); // nameOrLabel 누락 시 typeFallback 사용
        String containerName = resolveContainerName(mysql.getContainerName(), serviceName);
        String image = resolveImage(mysql.getImageVersion());

        context.getEnvVars().put("MYSQL_DATABASE", env.getDatabaseName());
        context.getEnvVars().put("MYSQL_USER", env.getUsername());
        context.getEnvVars().put("MYSQL_PASSWORD", env.getUserPassword());
        context.getEnvVars().put("MYSQL_ROOT_PASSWORD", env.getRootPassword());

        StringBuilder yaml = new StringBuilder();
        yaml.append("  ").append(serviceName).append(":\n");
        yaml.append("    image: ").append(image).append('\n');
        yaml.append("    container_name: ").append(containerName).append('\n');
        yaml.append("    ports:\n");
        yaml.append("      - \"").append(mysql.getPort()).append(":3306\"\n");

        if (mysql.getVolumeName() != null && !mysql.getVolumeName().isBlank()) {
            yaml.append("    volumes:\n");
            yaml.append("      - ").append(mysql.getVolumeName().trim()).append(":/var/lib/mysql\n");
        }

        yaml.append("    env_file:\n");
        yaml.append("      - .env\n");
        yaml.append("    environment:\n");
        yaml.append("      MYSQL_DATABASE: ${MYSQL_DATABASE}\n");
        yaml.append("      MYSQL_USER: ${MYSQL_USER}\n");
        yaml.append("      MYSQL_PASSWORD: ${MYSQL_PASSWORD}\n");
        yaml.append("      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}\n");
        yaml.append("      TZ: Asia/Seoul\n");

        return yaml.toString();
    }

    private static String resolveContainerName(String containerName, String serviceName) {
        if (containerName != null && !containerName.isBlank()) {
            return containerName.trim();
        }
        return serviceName;
    }

    private static String resolveImage(String imageVersion) {
        if (imageVersion != null && !imageVersion.isBlank()) {
            return imageVersion.trim();
        }
        return DEFAULT_IMAGE;
    }
}
