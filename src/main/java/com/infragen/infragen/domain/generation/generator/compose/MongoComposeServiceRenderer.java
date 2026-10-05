package com.infragen.infragen.domain.generation.generator.compose;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.MongoDBComponent;
import com.infragen.infragen.domain.parsing.dto.response.MongoDBEnvComponent;
import com.infragen.infragen.global.enums.ComponentType;

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
}
