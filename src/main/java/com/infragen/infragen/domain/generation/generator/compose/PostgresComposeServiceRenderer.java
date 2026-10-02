package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLEnvComponent;
import com.infragen.infragen.global.enums.ComponentType;
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
        String containerName = resolveContainerName(postgres.getContainerName(), serviceName);
        String image = resolveImage(postgres.getImageVersion());

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

    private static String resolveContainerName(String containerName, String serviceName) {
        if (containerName != null && !containerName.isBlank()) {
            return containerName.trim();
        }
        return serviceName;
    }

    private static String resolveImage(String imageVersion) {
        if (imageVersion != null && !imageVersion.isBlank()) {
            return imageVersion.trim();
        }
        return DEFAULT_IMAGE;
    }
}
