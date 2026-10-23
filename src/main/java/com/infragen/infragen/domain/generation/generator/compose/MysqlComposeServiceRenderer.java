package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLEnvComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MysqlComposeServiceRenderer implements ComposeServiceRenderer {

    private static final String TYPE_LABEL = "MySQL";
    private static final String DEFAULT_IMAGE = "mysql:8.0";

    @Override
    public ComponentType getSupportedType() {
        return ComponentType.MYSQL;
    }

    @Override
    public String render(BaseComponent component, ComposeGenerationContext context) {
        MySQLComponent mysql = (MySQLComponent) component;
        MySQLEnvComponent env = mysql.getEnv();
        if (env == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }

        String serviceName = ComposeYamlSupport.toServiceName(
            mysql.getContainerName(), null, TYPE_LABEL); // nameOrLabel 누락 시 typeFallback 사용
        String containerName = ComposeYamlSupport.resolveContainerName(mysql.getContainerName(), serviceName);
        String image = ComposeYamlSupport.resolveImage(mysql.getImageVersion(), DEFAULT_IMAGE);

        context.getEnvVars().put("MYSQL_DATABASE", env.getDatabaseName());
        context.getEnvVars().put("MYSQL_USER", env.getUsername());
        context.getEnvVars().put("MYSQL_PASSWORD", env.getUserPassword());
        context.getEnvVars().put("MYSQL_ROOT_PASSWORD", env.getRootPassword());

        StringBuilder yaml = new StringBuilder();
        yaml.append("  ").append(serviceName).append(":\n");
        yaml.append("    image: ").append(image).append('\n');
        yaml.append("    container_name: ").append(containerName).append('\n');
        yaml.append("    ports:\n");
        yaml.append("      - \"").append(mysql.getPort()).append(":3306\"\n");

        if (mysql.getVolumeName() != null && !mysql.getVolumeName().isBlank()) {
            yaml.append("    volumes:\n");
            yaml.append("      - ").append(mysql.getVolumeName().trim()).append(":/var/lib/mysql\n");
        }

        yaml.append("    env_file:\n");
        yaml.append("      - .env\n");
        yaml.append("    environment:\n");
        yaml.append("      MYSQL_DATABASE: ${MYSQL_DATABASE}\n");
        yaml.append("      MYSQL_USER: ${MYSQL_USER}\n");
        yaml.append("      MYSQL_PASSWORD: ${MYSQL_PASSWORD}\n");
        yaml.append("      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}\n");
        yaml.append("      TZ: Asia/Seoul\n");

        return yaml.toString();
    }

    /**
     * @return 호스트 접속 주소, {@code .env}에 들어 있는 초기화 변수 이름, 첫 기동에서만 적용된다는 점을 안내하는 섹션
     */
    @Override
    public Optional<String> guideSection(BaseComponent component, ComposeGenerationContext context) {
        MySQLComponent mysql = (MySQLComponent) component;
        return Optional.of("""
            ## MySQL

            MySQL 컨테이너에는 호스트에서 `localhost:%d`로 접속합니다.

            - `.env`에 그래프에 입력한 `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` 값이 들어 있고, 컨테이너가 이 값으로 초기화됩니다. 앱과 연결했다면 앱이 읽을 `MYSQL_HOST`, `MYSQL_PORT`도 들어 있습니다.
            - 초기화 변수는 데이터 디렉터리가 비어 있는 첫 기동에서만 적용됩니다. 기존 데이터가 남아 있는 볼륨으로 다시 기동하면 `.env` 값을 바꿔도 이미 만든 DB와 계정은 바뀌지 않습니다.
            """.formatted(mysql.getPort()));
    }
}
