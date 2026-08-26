package com.infragen.infragen.domain.generation.generator.compose;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.RedisComponent;
import com.infragen.infragen.global.enums.ComponentType;

/** 호스트에서 실행하는 애플리케이션의 Redis 연결 환경변수를 생성한다. */
@Component
public class RedisHostAppEnvContributor implements HostAppEnvContributor {

    private static final String LOCALHOST = "localhost";

    @Override
    public ComponentType getDependencyType() {
        return ComponentType.REDIS;
    }

    @Override
    public void contributeHostAppEnv(
        BaseComponent dependency,
        BaseComponent application,
        ComposeGenerationContext ctx
    ) {
        RedisComponent redis = (RedisComponent) dependency;
        if (redis.getPassword() == null || redis.getPassword().isBlank()) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }

        ctx.getEnvVars().put("REDIS_HOST", LOCALHOST);
        ctx.getEnvVars().put("REDIS_PORT", String.valueOf(redis.getPort()));
        ctx.getEnvVars().put("REDIS_PASSWORD", redis.getPassword());
    }
}
