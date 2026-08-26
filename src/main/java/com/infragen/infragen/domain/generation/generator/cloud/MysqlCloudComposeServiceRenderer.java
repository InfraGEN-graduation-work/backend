package com.infragen.infragen.domain.generation.generator.cloud;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.global.enums.ComponentType;

/** 파싱된 MySQL 컴포넌트를 CLOUD_DEPLOY Compose service로 렌더링한다. */
@Component
public class MysqlCloudComposeServiceRenderer implements CloudComposeServiceRenderer {

    @Override
    public String getServiceName() {
        return "mysql";
    }

    @Override
    public boolean isEnabled(CloudDeployContext context) {
        return context.hasComponent(ComponentType.MYSQL);
    }

    @Override
    public boolean isDependency(CloudDeployContext context) {
        return context.hasIncomingDependency(ComponentType.MYSQL);
    }

    @Override
    public String render(CloudDeployContext context) {
        MySQLComponent mysql = context.firstComponent(ComponentType.MYSQL, MySQLComponent.class);
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
}
