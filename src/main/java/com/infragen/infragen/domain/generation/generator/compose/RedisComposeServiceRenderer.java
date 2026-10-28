package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.RedisComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.Optional;
import org.springframework.stereotype.Component;

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
        yaml.append("  ").append(ComposeYamlSupport.toYamlString(serviceName)).append(":\n");
        yaml.append("    image: ").append(ComposeYamlSupport.toYamlString(redis.getImageVersion().trim())).append('\n');
        yaml.append("    container_name: ").append(ComposeYamlSupport.toYamlString(containerName)).append('\n');
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

    /**
     * @return 호스트 접속 주소와 {@code .env}에 들어 있는 비밀번호 변수 이름을 안내하는 섹션
     */
    @Override
    public Optional<String> guideSection(BaseComponent component, ComposeGenerationContext context) {
        RedisComponent redis = (RedisComponent) component;
        return Optional.of("""
            ## Redis

            Redis 컨테이너에는 호스트에서 `localhost:%d`로 접속합니다.

            - `local/.env`에 그래프에 입력한 `REDIS_PASSWORD` 값이 들어 있고, Redis는 `--requirepass`로 이 값을 받습니다. 앱과 연결했다면 앱이 읽을 `REDIS_HOST`, `REDIS_PORT`도 들어 있습니다.
            - 비밀번호는 컨테이너를 만들 때마다 명령 인자로 적용됩니다. `local/.env` 값을 바꾼 뒤 `docker compose -f local/docker-compose.yml up -d`를 실행하면 Redis 컨테이너가 새 값으로 다시 만들어집니다.
            """.formatted(redis.getPort()));
    }
}
