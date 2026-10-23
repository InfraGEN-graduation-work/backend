package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.MongoDBComponent;
import com.infragen.infragen.domain.parsing.dto.response.MongoDBEnvComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 파싱된 MongoDB 컴포넌트를 LOCAL_DEV Compose service로 렌더링한다.
 *
 * <p>컨테이너 초기화 값({@code MONGO_INITDB_ROOT_USERNAME/PASSWORD/DATABASE})의 원본을 {@code MONGO_USER/PASSWORD/DATABASE}로
 * {@code .env}에 넣고 Compose에서는 변수로 참조한다. 호스트 앱 접속 변수는 {@link MongoHostAppEnvContributor}가 만든다.
 */
@Component
public class MongoComposeServiceRenderer implements ComposeServiceRenderer {

    private static final String TYPE_LABEL = "MongoDB";
    private static final String DEFAULT_IMAGE = "mongo:8.0";
    private static final String DATA_DIR = "/data/db";

    @Override
    public ComponentType getSupportedType() {
        return ComponentType.MONGODB;
    }

    @Override
    public String render(BaseComponent component, ComposeGenerationContext context) {
        MongoDBComponent mongo = (MongoDBComponent) component;
        MongoDBEnvComponent env = mongo.getEnv();
        if (env == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }

        String serviceName = ComposeYamlSupport.toServiceName(mongo.getContainerName(), null, TYPE_LABEL);
        String containerName = ComposeYamlSupport.resolveContainerName(mongo.getContainerName(), serviceName);
        String image = ComposeYamlSupport.resolveImage(mongo.getImageVersion(), DEFAULT_IMAGE);

        context.getEnvVars().put("MONGO_DATABASE", env.getDatabaseName());
        context.getEnvVars().put("MONGO_USER", env.getUsername());
        context.getEnvVars().put("MONGO_PASSWORD", env.getPassword());

        StringBuilder yaml = new StringBuilder();
        yaml.append("  ").append(serviceName).append(":\n");
        yaml.append("    image: ").append(image).append('\n');
        yaml.append("    container_name: ").append(containerName).append('\n');
        yaml.append("    ports:\n");
        yaml.append("      - \"").append(mongo.getPort()).append(":27017\"\n");

        if (mongo.getVolumeName() != null && !mongo.getVolumeName().isBlank()) {
            yaml.append("    volumes:\n");
            yaml.append("      - ").append(mongo.getVolumeName().trim()).append(':').append(DATA_DIR).append('\n');
        }

        yaml.append("    env_file:\n");
        yaml.append("      - .env\n");
        yaml.append("    environment:\n");
        yaml.append("      MONGO_INITDB_ROOT_USERNAME: ${MONGO_USER}\n");
        yaml.append("      MONGO_INITDB_ROOT_PASSWORD: ${MONGO_PASSWORD}\n");
        yaml.append("      MONGO_INITDB_DATABASE: ${MONGO_DATABASE}\n");
        yaml.append("      TZ: Asia/Seoul\n");

        return yaml.toString();
    }

    /**
     * @return 호스트 접속 주소, {@code .env}에 들어 있는 초기화 변수 이름, root 계정의 인증 DB를 안내하는 섹션
     */
    @Override
    public Optional<String> guideSection(BaseComponent component, ComposeGenerationContext context) {
        MongoDBComponent mongo = (MongoDBComponent) component;
        return Optional.of("""
            ## MongoDB

            MongoDB 컨테이너에는 호스트에서 `localhost:%d`로 접속합니다.

            - `local/.env`에 그래프에 입력한 `MONGO_DATABASE`, `MONGO_USER`, `MONGO_PASSWORD` 값이 들어 있고, 컨테이너가 이 값으로 초기화됩니다. 앱과 연결했다면 앱이 읽을 `MONGO_HOST`, `MONGO_PORT`도 들어 있습니다.
            - `MONGO_USER` 계정은 `%s` DB에 만들어집니다. 앱에서 접속할 때 인증 DB를 `%s` 값으로 지정해야 합니다.
            - 초기화 변수는 데이터 디렉터리가 비어 있는 첫 기동에서만 적용됩니다. 기존 데이터가 남아 있는 볼륨으로 다시 기동하면 `local/.env` 값을 바꿔도 이미 만든 DB와 계정은 바뀌지 않습니다.
            """.formatted(mongo.getPort(), MongoHostAppEnvContributor.AUTHENTICATION_DATABASE,
            MongoHostAppEnvContributor.AUTHENTICATION_DATABASE));
    }
}
