package com.infragen.infragen.domain.generation.generator.cloud;

import com.infragen.infragen.domain.parsing.dto.response.RedisComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import org.springframework.stereotype.Component;

/** 파싱된 Redis 컴포넌트를 CLOUD_DEPLOY Compose service로 렌더링한다. */
@Component
public class RedisCloudComposeServiceRenderer implements CloudComposeServiceRenderer {

    // Cloud는 Compose 내부 네트워크로 접속하므로 사용자 입력(호스트) 포트가 아니라 컨테이너 포트를 쓴다.
    private static final int CONTAINER_PORT = 6379;

    /** @return 이 renderer가 담당하는 Redis component type */
    @Override
    public ComponentType getSupportedType() {
        return ComponentType.REDIS;
    }

    @Override
    public String getServiceName() {
        return "redis";
    }

    /** 그래프에서 애플리케이션으로 연결된 Redis만 Cloud Compose에 포함한다. */
    @Override
    public boolean isEnabled(CloudDeployContext context) {
        return context.hasIncomingDependency(ComponentType.REDIS);
    }

    @Override
    public boolean isDependency(CloudDeployContext context) {
        return context.hasIncomingDependency(ComponentType.REDIS);
    }

    @Override
    public String render(CloudDeployContext context) {
        RedisComponent redis = context.dependencyComponent(ComponentType.REDIS, RedisComponent.class);
        String volumeName = redis.getVolumeName();
        String volumeMount = volumeName == null || volumeName.isBlank()
            ? ""
            : """
            volumes:
              - %s:/data
        """.formatted(volumeName.trim());

        return """

          redis:
            image: %s
            env_file:
              - .env
            command: ["redis-server", "--requirepass", "${REDIS_PASSWORD:?외부 .env에 설정 필요}", "--appendonly", "yes"]
        %s""".formatted(redis.getImageVersion(), volumeMount);
    }

    /** 앱 컨테이너가 Compose 서비스 DNS로 Redis에 접속할 {@code REDIS_HOST/PORT/PASSWORD}를 만든다. */
    @Override
    public SequencedMap<String, String> applicationEnvironment() {
        SequencedMap<String, String> environment = new LinkedHashMap<>();
        environment.put("REDIS_HOST", getServiceName());
        environment.put("REDIS_PORT", "\"" + CONTAINER_PORT + "\"");
        environment.put("REDIS_PASSWORD", "\"${REDIS_PASSWORD:?외부 .env에 설정 필요}\"");
        return environment;
    }
}
