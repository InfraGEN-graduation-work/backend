package com.infragen.infragen.domain.generation.generator.cloud;

import com.infragen.infragen.domain.generation.generator.application.DatabaseConnection;
import com.infragen.infragen.domain.parsing.dto.response.MongoDBComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;
import org.springframework.stereotype.Component;

/**
 * 파싱된 MongoDB 컴포넌트를 CLOUD_DEPLOY Compose service로 렌더링한다.
 *
 * <p>비밀값은 서버의 외부 {@code .env}에서 읽고, 값이 없으면 Compose가 기동 전에 실패하도록 {@code :?}로 참조한다.
 * 앱 컨테이너용 중립 변수와 DB 접속 정보도 제공한다.
 */
@Component
public class MongoCloudComposeServiceRenderer implements CloudComposeServiceRenderer {

    private static final String DATA_DIR = "/data/db";
    // 공식 이미지는 MONGO_INITDB_ROOT_*로 만든 root 계정을 admin DB에 만들므로 앱이 그 계정을 찾을 DB를 알려야 한다.
    private static final String AUTHENTICATION_DATABASE = "admin";
    // Cloud는 Compose 내부 네트워크로 접속하므로 사용자 입력(호스트) 포트가 아니라 컨테이너 포트를 쓴다.
    private static final int CONTAINER_PORT = 27017;

    @Override
    public ComponentType getSupportedType() {
        return ComponentType.MONGODB;
    }

    @Override
    public String getServiceName() {
        return "mongodb";
    }

    /** 그래프에서 애플리케이션으로 연결된 MongoDB만 Cloud Compose에 포함한다. */
    @Override
    public boolean isEnabled(CloudDeployContext context) {
        return context.hasIncomingDependency(ComponentType.MONGODB);
    }

    @Override
    public boolean isDependency(CloudDeployContext context) {
        return context.hasIncomingDependency(ComponentType.MONGODB);
    }

    @Override
    public String render(CloudDeployContext context) {
        MongoDBComponent mongo = context.dependencyComponent(ComponentType.MONGODB, MongoDBComponent.class);
        String volumeName = mongo.getVolumeName();
        String volumeMount = volumeName == null || volumeName.isBlank()
            ? ""
            : """
            volumes:
              - %s:%s
        """.formatted(volumeName.trim(), DATA_DIR);

        return """

          mongodb:
            image: %s
            env_file:
              - .env
            environment:
              MONGO_INITDB_ROOT_USERNAME: ${MONGO_USER:?외부 .env에 설정 필요}
              MONGO_INITDB_ROOT_PASSWORD: ${MONGO_PASSWORD:?외부 .env에 설정 필요}
              MONGO_INITDB_DATABASE: ${MONGO_DATABASE:?외부 .env에 설정 필요}
        %s""".formatted(mongo.getImageVersion(), volumeMount);
    }

    /** 앱 컨테이너가 Compose 서비스 DNS로 MongoDB에 접속할 {@code MONGO_HOST/PORT}를 만든다. */
    @Override
    public SequencedMap<String, String> applicationEnvironment() {
        SequencedMap<String, String> environment = new LinkedHashMap<>();
        environment.put("MONGO_HOST", getServiceName());
        environment.put("MONGO_PORT", "\"" + CONTAINER_PORT + "\"");
        return environment;
    }

    /** DB 이름, 계정, 비밀번호는 서버의 외부 {@code .env} 값을 참조한다. */
    @Override
    public Optional<DatabaseConnection> databaseConnection() {
        return Optional.of(new DatabaseConnection(
            "mongodb",
            false,
            getServiceName(),
            String.valueOf(CONTAINER_PORT),
            "${MONGO_DATABASE:?외부 .env에 설정 필요}",
            "${MONGO_USER:?외부 .env에 설정 필요}",
            "${MONGO_PASSWORD:?외부 .env에 설정 필요}",
            Optional.of(AUTHENTICATION_DATABASE)
        ));
    }
}
