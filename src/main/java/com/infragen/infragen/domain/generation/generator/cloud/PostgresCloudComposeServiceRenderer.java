package com.infragen.infragen.domain.generation.generator.cloud;

import com.infragen.infragen.domain.generation.generator.application.DatabaseConnection;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;
import org.springframework.stereotype.Component;

/**
 * 파싱된 PostgreSQL 컴포넌트를 CLOUD_DEPLOY Compose service로 렌더링한다.
 *
 * <p>비밀값은 서버의 외부 {@code .env}에서 읽고, 값이 없으면 Compose가 기동 전에 실패하도록 {@code :?}로 참조한다.
 * 앱 컨테이너용 중립 변수와 DB 접속 정보도 제공한다.
 */
@Component
public class PostgresCloudComposeServiceRenderer implements CloudComposeServiceRenderer {

    private static final String DATA_DIR = "/var/lib/postgresql/data";
    // Cloud는 Compose 내부 네트워크로 접속하므로 사용자 입력(호스트) 포트가 아니라 컨테이너 포트를 쓴다.
    private static final int CONTAINER_PORT = 5432;

    @Override
    public ComponentType getSupportedType() {
        return ComponentType.POSTGRESQL;
    }

    @Override
    public String getServiceName() {
        return "postgres";
    }

    /**
     * 그래프에서 애플리케이션으로 연결된 PostgreSQL만 Cloud Compose에 포함한다.
     */
    @Override
    public boolean isEnabled(CloudDeployContext context) {
        return context.hasIncomingDependency(ComponentType.POSTGRESQL);
    }

    @Override
    public boolean isDependency(CloudDeployContext context) {
        return context.hasIncomingDependency(ComponentType.POSTGRESQL);
    }

    @Override
    public String render(CloudDeployContext context) {
        PostgreSQLComponent postgres = context.dependencyComponent(
                ComponentType.POSTGRESQL, PostgreSQLComponent.class);
        String volumeName = postgres.getVolumeName();
        String volumeMount = volumeName == null || volumeName.isBlank()
                ? ""
                : """
                            volumes:
                              - %s:%s
                        """.formatted(volumeName.trim(), DATA_DIR);

        // 18+ 이미지는 기본 PGDATA가 /var/lib/postgresql/{major}/docker라서 마운트 경로로 고정해야 데이터가 볼륨에 남는다.
        return """
                
                  postgres:
                    image: %s
                    env_file:
                      - .env
                    environment:
                      POSTGRES_DB: ${POSTGRES_DB:?외부 .env에 설정 필요}
                      POSTGRES_USER: ${POSTGRES_USER:?외부 .env에 설정 필요}
                      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?외부 .env에 설정 필요}
                      PGDATA: %s
                %s""".formatted(postgres.getImageVersion(), DATA_DIR, volumeMount);
    }

    /** 앱 컨테이너가 Compose 서비스 DNS로 PostgreSQL에 접속할 {@code POSTGRES_HOST/PORT}를 만든다. */
    @Override
    public SequencedMap<String, String> applicationEnvironment() {
        SequencedMap<String, String> environment = new LinkedHashMap<>();
        environment.put("POSTGRES_HOST", getServiceName());
        environment.put("POSTGRES_PORT", "\"" + CONTAINER_PORT + "\"");
        return environment;
    }

    /** DB 이름, 계정, 비밀번호는 서버의 외부 {@code .env} 값을 참조한다. */
    @Override
    public Optional<DatabaseConnection> databaseConnection() {
        return Optional.of(new DatabaseConnection(
            "postgresql",
            true,
            getServiceName(),
            String.valueOf(CONTAINER_PORT),
            "${POSTGRES_DB:?외부 .env에 설정 필요}",
            "${POSTGRES_USER:?외부 .env에 설정 필요}",
            "${POSTGRES_PASSWORD:?외부 .env에 설정 필요}"
        ));
    }
}
