package com.infragen.infragen.domain.generation.generator.cloud;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.response.RedisComponent;
import com.infragen.infragen.global.enums.ComponentType;

/** 파싱된 Redis 컴포넌트를 CLOUD_DEPLOY Compose service로 렌더링한다. */
@Component
public class RedisCloudComposeServiceRenderer implements CloudComposeServiceRenderer {

    @Override
    public String getServiceName() {
        return "redis";
    }

    @Override
    public boolean isEnabled(CloudDeployContext context) {
        return context.hasComponent(ComponentType.REDIS);
    }

    @Override
    public boolean isDependency(CloudDeployContext context) {
        return context.hasIncomingDependency(ComponentType.REDIS);
    }

    @Override
    public String render(CloudDeployContext context) {
        RedisComponent redis = context.firstComponent(ComponentType.REDIS, RedisComponent.class);
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
}
