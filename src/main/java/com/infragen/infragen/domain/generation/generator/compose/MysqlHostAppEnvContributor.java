package com.infragen.infragen.domain.generation.generator.compose;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLEnvComponent;
import com.infragen.infragen.global.enums.ComponentType;

@Component
public class MysqlHostAppEnvContributor implements HostAppEnvContributor {
    private static final String LOCALHOST = "localhost";

    @Override
    public ComponentType getDependencyType() {
        return ComponentType.MYSQL;
    }

    // 호스트에서 bootRun 시 사용할 JDBC·credential env (compose 컨테이너 DNS 아님)
    @Override
    public void contributeHostAppEnv(
        BaseComponent dependency,
        BaseComponent application,
        ComposeGenerationContext ctx
    ) {
        MySQLComponent mysql = (MySQLComponent) dependency;
        MySQLEnvComponent env = mysql.getEnv();
        int hostPort = mysql.getPort();

        String jdbcUrl = "jdbc:mysql://"
            + LOCALHOST
            + ":"
            + hostPort
            + "/"
            + env.getDatabaseName();

        ctx.getEnvVars().put("SPRING_DATASOURCE_URL", jdbcUrl);
        ctx.getEnvVars().put("SPRING_DATASOURCE_USERNAME", env.getUsername());
        ctx.getEnvVars().put("SPRING_DATASOURCE_PASSWORD", env.getUserPassword());
        ctx.getEnvVars().put("MYSQL_HOST", LOCALHOST);
        ctx.getEnvVars().put("MYSQL_PORT", String.valueOf(hostPort));
    }
}
