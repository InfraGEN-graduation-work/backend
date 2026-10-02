package com.infragen.infragen.domain.generation.generator.cloud;

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
        %s""".formatted(mysql.getImageVersion(), volumeMount);
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
    public Optional<JdbcConnection> jdbcConnection() {
        return Optional.of(new JdbcConnection(
            "\"jdbc:mysql://" + getServiceName() + ":" + CONTAINER_PORT
                + "/${MYSQL_DATABASE:?외부 .env에 설정 필요}\"",
            "\"${MYSQL_USER:?외부 .env에 설정 필요}\"",
            "\"${MYSQL_PASSWORD:?외부 .env에 설정 필요}\""
        ));
    }
}
