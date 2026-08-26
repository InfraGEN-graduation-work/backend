package com.infragen.infragen.domain.generation.generator.compose;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.RedisComponent;
import com.infragen.infragen.global.enums.ComponentType;

/** 파싱된 Redis 컴포넌트를 LOCAL_DEV Compose service로 렌더링한다. */
@Component
public class RedisComposeServiceRenderer implements ComposeServiceRenderer {

    private static final String TYPE_LABEL = "Redis";
    private static final String REDIS_PORT = "6379";

    @Override
    public ComponentType getSupportedType() {
        return ComponentType.REDIS;
    }

    @Override
    public String render(BaseComponent component, ComposeGenerationContext context) {
        RedisComponent redis = (RedisComponent) component;
        String serviceName = ComposeYamlSupport.toServiceName(
            redis.getContainerName(), null, TYPE_LABEL);
        String containerName = resolveContainerName(redis.getContainerName(), serviceName);

        StringBuilder yaml = new StringBuilder();
        yaml.append("  ").append(serviceName).append(":\n");
        yaml.append("    image: ").append(redis.getImageVersion().trim()).append('\n');
        yaml.append("    container_name: ").append(containerName).append('\n');
        yaml.append("    ports:\n");
        yaml.append("      - \"").append(redis.getPort()).append(":").append(REDIS_PORT)
            .append("\"\n");

        if (redis.getVolumeName() != null && !redis.getVolumeName().isBlank()) {
            yaml.append("    volumes:\n");
            yaml.append("      - ").append(redis.getVolumeName().trim()).append(":/data\n");
        }

        yaml.append("    env_file:\n");
        yaml.append("      - .env\n");
        yaml.append("    command: redis-server --requirepass ${REDIS_PASSWORD} --appendonly yes\n");

        return yaml.toString();
    }

    private static String resolveContainerName(String containerName, String serviceName) {
        if (containerName != null && !containerName.isBlank()) {
            return containerName.trim();
        }
        return serviceName;
    }
}
