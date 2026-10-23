package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLEnvComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 파싱된 PostgreSQL 컴포넌트를 LOCAL_DEV Compose service로 렌더링한다.
 * <p>
 * 컨테이너 초기화 값({@code POSTGRES_DB/USER/PASSWORD})을 {@code .env}에 넣고 Compose에서는 변수로 참조한다. 호스트 앱 접속 변수는
 * {@link PostgresHostAppEnvContributor}가 만든다.
 */
@Component
public class PostgresComposeServiceRenderer implements ComposeServiceRenderer {

    private static final String TYPE_LABEL = "PostgreSQL";
    private static final String DEFAULT_IMAGE = "postgres:17";
    private static final String DATA_DIR = "/var/lib/postgresql/data";

    @Override
    public ComponentType getSupportedType() {
        return ComponentType.POSTGRESQL;
    }

    @Override
    public String render(BaseComponent component, ComposeGenerationContext context) {
        PostgreSQLComponent postgres = (PostgreSQLComponent) component;
        PostgreSQLEnvComponent env = postgres.getEnv();
        if (env == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }

        String serviceName = ComposeYamlSupport.toServiceName(
                postgres.getContainerName(), null, TYPE_LABEL);
        String containerName = ComposeYamlSupport.resolveContainerName(postgres.getContainerName(), serviceName);
        String image = ComposeYamlSupport.resolveImage(postgres.getImageVersion(), DEFAULT_IMAGE);

        context.getEnvVars().put("POSTGRES_DB", env.getDatabaseName());
        context.getEnvVars().put("POSTGRES_USER", env.getUsername());
        context.getEnvVars().put("POSTGRES_PASSWORD", env.getPassword());

        StringBuilder yaml = new StringBuilder();
        yaml.append("  ").append(serviceName).append(":\n");
        yaml.append("    image: ").append(image).append('\n');
        yaml.append("    container_name: ").append(containerName).append('\n');
        yaml.append("    ports:\n");
        yaml.append("      - \"").append(postgres.getPort()).append(":5432\"\n");

        if (postgres.getVolumeName() != null && !postgres.getVolumeName().isBlank()) {
            yaml.append("    volumes:\n");
            yaml.append("      - ").append(postgres.getVolumeName().trim())
                    .append(':').append(DATA_DIR).append('\n');
        }

        yaml.append("    env_file:\n");
        yaml.append("      - .env\n");
        yaml.append("    environment:\n");
        yaml.append("      POSTGRES_DB: ${POSTGRES_DB}\n");
        yaml.append("      POSTGRES_USER: ${POSTGRES_USER}\n");
        yaml.append("      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}\n");
        // 18+ 이미지는 기본 PGDATA가 /var/lib/postgresql/{major}/docker라서 마운트 경로로 고정해야 데이터가 볼륨에 남는다.
        yaml.append("      PGDATA: ").append(DATA_DIR).append('\n');
        yaml.append("      TZ: Asia/Seoul\n");

        return yaml.toString();
    }

    /**
     * @return 호스트 접속 주소, {@code .env}에 들어 있는 초기화 변수 이름, 첫 기동에서만 적용된다는 점을 안내하는 섹션
     */
    @Override
    public Optional<String> guideSection(BaseComponent component, ComposeGenerationContext context) {
        PostgreSQLComponent postgres = (PostgreSQLComponent) component;
        return Optional.of("""
            ## PostgreSQL

            PostgreSQL 컨테이너에는 호스트에서 `localhost:%d`로 접속합니다.

            - `local/.env`에 그래프에 입력한 `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` 값이 들어 있고, 컨테이너가 이 값으로 초기화됩니다. 앱과 연결했다면 앱이 읽을 `POSTGRES_HOST`, `POSTGRES_PORT`도 들어 있습니다.
            - 초기화 변수는 데이터 디렉터리가 비어 있는 첫 기동에서만 적용됩니다. 기존 데이터가 남아 있는 볼륨으로 다시 기동하면 `local/.env` 값을 바꿔도 이미 만든 DB와 계정은 바뀌지 않습니다.
            """.formatted(postgres.getPort()));
    }
}
