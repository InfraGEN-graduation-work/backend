package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.RedisComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import org.springframework.stereotype.Component;

/** 호스트에서 실행하는 애플리케이션의 Redis 연결 환경변수를 생성한다. */
@Component
public class RedisHostAppEnvContributor implements HostAppEnvContributor {

    private static final String LOCALHOST = "localhost";

    @Override
    public ComponentType getDependencyType() {
        return ComponentType.REDIS;
    }

    /**
     * @throws IaCGenerationException Redis password가 없는 경우
     */
    @Override
    public SequencedMap<String, String> hostAppEnvironment(BaseComponent dependency) {
        RedisComponent redis = (RedisComponent) dependency;
        if (redis.getPassword() == null || redis.getPassword().isBlank()) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }

        SequencedMap<String, String> environment = new LinkedHashMap<>();
        environment.put("REDIS_HOST", LOCALHOST);
        environment.put("REDIS_PORT", String.valueOf(redis.getPort()));
        environment.put("REDIS_PASSWORD", redis.getPassword());
        return environment;
    }
}
