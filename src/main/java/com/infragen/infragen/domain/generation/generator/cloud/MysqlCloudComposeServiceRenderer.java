package com.infragen.infragen.domain.generation.generator.cloud;

import com.infragen.infragen.domain.generation.generator.application.DatabaseConnection;
import com.infragen.infragen.domain.generation.generator.compose.ComposeYamlSupport;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;
import org.springframework.stereotype.Component;

/** 파싱된 MySQL 컴포넌트를 CLOUD_DEPLOY Compose service로 렌더링한다. */
@Component
public class MysqlCloudComposeServiceRenderer implements CloudComposeServiceRenderer {

    // Cloud는 Compose 내부 네트워크로 접속하므로 사용자 입력(호스트) 포트가 아니라 컨테이너 포트를 쓴다.
    private static final int CONTAINER_PORT = 3306;

    /** @return 이 renderer가 담당하는 MySQL component type */
    @Override
    public ComponentType getSupportedType() {
        return ComponentType.MYSQL;
    }

    @Override
    public String getServiceName() {
        return "mysql";
    }

    /** 그래프에서 애플리케이션으로 연결된 MySQL만 Cloud Compose에 포함한다. */
    @Override
    public boolean isEnabled(CloudDeployContext context) {
        return context.hasIncomingDependency(ComponentType.MYSQL);
    }

    @Override
    public boolean isDependency(CloudDeployContext context) {
        return context.hasIncomingDependency(ComponentType.MYSQL);
    }

    @Override
    public String render(CloudDeployContext context) {
        MySQLComponent mysql = context.dependencyComponent(ComponentType.MYSQL, MySQLComponent.class);
        String volumeName = mysql.getVolumeName();
        String volumeMount = volumeName == null || volumeName.isBlank()
            ? ""
            : """
            volumes:
              - %s:/var/lib/mysql
        """.formatted(volumeName.trim());

        return """

          mysql:
            image: %s
            env_file:
              - .env
            environment:
              MYSQL_DATABASE: ${MYSQL_DATABASE:?외부 .env에 설정 필요}
              MYSQL_USER: ${MYSQL_USER:?외부 .env에 설정 필요}
              MYSQL_PASSWORD: ${MYSQL_PASSWORD:?외부 .env에 설정 필요}
              MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?외부 .env에 설정 필요}
        %s""".formatted(ComposeYamlSupport.toYamlString(ComposeYamlSupport.resolveImage(mysql.getImageVersion(), null)), volumeMount);
    }

    /** 앱 컨테이너가 Compose 서비스 DNS로 MySQL에 접속할 {@code MYSQL_HOST/PORT}를 만든다. */
    @Override
    public SequencedMap<String, String> applicationEnvironment() {
        SequencedMap<String, String> environment = new LinkedHashMap<>();
        environment.put("MYSQL_HOST", getServiceName());
        environment.put("MYSQL_PORT", "\"" + CONTAINER_PORT + "\"");
        return environment;
    }

    /** DB 이름, 계정, 비밀번호는 서버의 외부 {@code .env} 값을 참조한다. */
    @Override
    public Optional<DatabaseConnection> databaseConnection() {
        return Optional.of(new DatabaseConnection(
            "mysql",
            true,
            getServiceName(),
            String.valueOf(CONTAINER_PORT),
            "${MYSQL_DATABASE:?외부 .env에 설정 필요}",
            "${MYSQL_USER:?외부 .env에 설정 필요}",
            "${MYSQL_PASSWORD:?외부 .env에 설정 필요}"
        ));
    }

    /**
     * @return 외부 {@code .env}에 설정할 MySQL 변수와 첫 기동에서만 적용된다는 점을 안내하는 섹션
     */
    @Override
    public Optional<String> guideSection(CloudDeployContext context) {
        // 변수 이름은 render의 ${변수:?} 참조와 같아야 한다. TerraformIaCGeneratorTest가 Compose 출력과 대조한다.
        return Optional.of("""
            ## MySQL

            `mysql` 서비스는 앱이 Compose 네트워크 안에서 `mysql:%d` 주소로 접속하는 DB입니다. 앱 컨테이너에는 `MYSQL_HOST`, `MYSQL_PORT`가 들어갑니다.

            ### 외부 `.env`에 설정할 변수

            `docker-compose.cloud.yml`과 같은 디렉터리의 `.env` 파일에 `변수=값` 형식으로 한 줄에 하나씩 적어 주세요. 값이 없거나 비어 있으면 Compose가 실행을 멈춥니다.

            - `MYSQL_DATABASE`: 처음 기동할 때 만들 DB 이름
            - `MYSQL_USER`: 앱이 접속할 계정 이름. `MYSQL_DATABASE`에 모든 권한을 받습니다.
            - `MYSQL_PASSWORD`: `MYSQL_USER`의 비밀번호
            - `MYSQL_ROOT_PASSWORD`: `root` 계정의 비밀번호

            ### 알아둘 점

            - 위 변수는 데이터 디렉터리가 비어 있는 첫 기동에서만 적용됩니다. 기존 데이터가 남아 있는 볼륨으로 다시 기동하면 값을 바꿔도 이미 만든 DB와 계정은 바뀌지 않습니다.
            """.formatted(CONTAINER_PORT));
    }
}
