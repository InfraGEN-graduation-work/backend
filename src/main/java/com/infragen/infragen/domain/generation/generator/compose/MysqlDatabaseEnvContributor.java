package com.infragen.infragen.domain.generation.generator.compose;

import java.util.List;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.MySQLEnvComponent;
import com.infragen.infragen.global.enums.ComponentType;

@Component
public class MysqlDatabaseEnvContributor implements DatabaseEnvContributor {

    private static final String MYSQL_TYPE_LABEL = "MySQL";
    private static final int MYSQL_CONTAINER_PORT = 3306;

    @Override
    public ComponentType getDatabaseType() {
        return ComponentType.MYSQL;
    }

    // JDBC 연결 정보와 credential env를 context에 추가
    @Override
    public void contributeConnection(
        BaseComponent db,
        BaseComponent app,
        ComposeGenerationContext ctx,
        List<String> envKeys
    ) {
        MySQLComponent mysql = (MySQLComponent) db;
        MySQLEnvComponent env = mysql.getEnv();

        String mysqlServiceName = ComposeYamlSupport.toServiceName(
            mysql.getContainerName(), null, MYSQL_TYPE_LABEL);

        String jdbcUrl = "jdbc:mysql://"
            + mysqlServiceName
            + ":"
            + MYSQL_CONTAINER_PORT
            + "/"
            + env.getDatabaseName();

        ctx.getEnvVars().put("SPRING_DATASOURCE_URL", jdbcUrl);
        ctx.getEnvVars().put("SPRING_DATASOURCE_USERNAME", env.getUsername());
        ctx.getEnvVars().put("SPRING_DATASOURCE_PASSWORD", env.getUserPassword());
        ctx.getEnvVars().put("MYSQL_HOST", mysqlServiceName);
        ctx.getEnvVars().put("MYSQL_PORT", String.valueOf(mysql.getPort()));

        envKeys.add("SPRING_DATASOURCE_URL");
        envKeys.add("SPRING_DATASOURCE_USERNAME");
        envKeys.add("SPRING_DATASOURCE_PASSWORD");
        envKeys.add("MYSQL_HOST");
        envKeys.add("MYSQL_PORT");
    }
}
