package com.infragen.infragen.domain.generation.generator.compose;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
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
        if (redis.getPassword() == null || redis.getPassword().isBlank()) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }

        String serviceName = ComposeYamlSupport.toServiceName(
            redis.getContainerName(), null, TYPE_LABEL);
        String containerName = ComposeYamlSupport.resolveContainerName(redis.getContainerName(), serviceName);

        // 앱과 연결되지 않은 Redis도 compose의 ${REDIS_PASSWORD}를 채우도록 render에서 등록한다.
        context.getEnvVars().put("REDIS_PASSWORD", redis.getPassword());

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
}
