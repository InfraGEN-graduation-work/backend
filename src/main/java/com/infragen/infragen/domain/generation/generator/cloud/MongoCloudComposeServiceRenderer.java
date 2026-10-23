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

    /**
     * @return 외부 {@code .env}에 설정할 MongoDB 변수와 root 계정의 인증 DB를 안내하는 섹션
     */
    @Override
    public Optional<String> guideSection(CloudDeployContext context) {
        // 변수 이름은 render의 ${변수:?} 참조와 같아야 한다. TerraformIaCGeneratorTest가 Compose 출력과 대조한다.
        return Optional.of("""
            ## MongoDB

            `mongodb` 서비스는 앱이 Compose 네트워크 안에서 `mongodb:%d` 주소로 접속하는 DB입니다. 앱 컨테이너에는 `MONGO_HOST`, `MONGO_PORT`가 들어갑니다.

            ### 외부 `.env`에 설정할 변수

            `docker-compose.cloud.yml`과 같은 디렉터리의 `.env` 파일에 `변수=값` 형식으로 한 줄에 하나씩 적어 주세요. 값이 없거나 비어 있으면 Compose가 실행을 멈춥니다.

            - `MONGO_DATABASE`: 앱이 쓸 DB 이름. 초기화 스크립트가 없으면 첫 기동 때 DB를 만들지 않고, 앱이 처음 데이터를 쓸 때 만들어집니다.
            - `MONGO_USER`: 처음 기동할 때 만들 `root` 권한 계정 이름
            - `MONGO_PASSWORD`: `MONGO_USER`의 비밀번호

            ### 알아둘 점

            - 위 변수는 데이터 디렉터리가 비어 있는 첫 기동에서만 적용됩니다. 기존 데이터가 남아 있는 볼륨으로 다시 기동하면 값을 바꿔도 이미 만든 DB와 계정은 바뀌지 않습니다.
            - `MONGO_USER` 계정은 `%s` DB에 만들어집니다. 앱에서 접속할 때 인증 DB를 `%s`로 지정해야 합니다.
            """.formatted(CONTAINER_PORT, AUTHENTICATION_DATABASE, AUTHENTICATION_DATABASE));
    }
}
