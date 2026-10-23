package com.infragen.infragen.domain.generation.generator.cloud;

import com.infragen.infragen.domain.parsing.dto.response.RedisComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.LinkedHashMap;
import java.util.Optional;
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

    /**
     * @return 외부 {@code .env}에 설정할 Redis 비밀번호 변수를 안내하는 섹션
     */
    @Override
    public Optional<String> guideSection(CloudDeployContext context) {
        // 변수 이름은 render의 ${변수:?} 참조와 같아야 한다. TerraformIaCGeneratorTest가 Compose 출력과 대조한다.
        return Optional.of("""
            ## Redis

            `redis` 서비스는 앱이 Compose 네트워크 안에서 `redis:%d` 주소로 접속하는 캐시입니다. 앱 컨테이너에는 `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`가 들어갑니다.

            ### 외부 `.env`에 설정할 변수

            `docker-compose.cloud.yml`과 같은 디렉터리의 `.env` 파일에 `변수=값` 형식으로 한 줄에 하나씩 적어 주세요. 값이 없거나 비어 있으면 Compose가 실행을 멈춥니다.

            - `REDIS_PASSWORD`: Redis 접속 비밀번호. Redis는 `--requirepass`로, 앱 컨테이너는 같은 이름의 환경변수로 이 값을 받습니다.

            ### 알아둘 점

            - 비밀번호는 DB 초기화 변수와 달리 컨테이너를 만들 때마다 명령 인자로 적용됩니다. 값을 바꾼 뒤 `docker compose -f docker-compose.cloud.yml up -d`를 실행하면 Redis와 앱 컨테이너가 새 값으로 다시 만들어집니다.
            - `--appendonly yes`로 데이터를 `/data`에 기록합니다.
            """.formatted(CONTAINER_PORT));
    }
}
