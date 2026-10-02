package com.infragen.infragen.domain.generation.generator.compose;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLEnvComponent;
import com.infragen.infragen.global.enums.ComponentType;

/**
 * 호스트에서 실행하는 애플리케이션의 PostgreSQL 접속 환경변수를 생성한다.
 *
 * <p>{@code POSTGRES_HOST/PORT}는 항상 만든다. {@code SPRING_DATASOURCE_*}는 앱에 연결된 DATABASE가 하나일 때만 만든다.
 * DB가 둘 이상이면 어느 DB가 기본 DataSource인지 정할 수 없기 때문이다.
 */
@Component
public class PostgresHostAppEnvContributor implements HostAppEnvContributor {

    private static final String LOCALHOST = "localhost";

    @Override
    public ComponentType getDependencyType() {
        return ComponentType.POSTGRESQL;
    }

    @Override
    public void contributeHostAppEnv(
        BaseComponent dependency,
        BaseComponent application,
        ComposeGenerationContext ctx
    ) {
        PostgreSQLComponent postgres = (PostgreSQLComponent) dependency;
        PostgreSQLEnvComponent env = postgres.getEnv();
        if (env == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }
        int hostPort = postgres.getPort();

        if (ctx.hasSingleDatabaseDependency(application.getNodeId())) {
            String jdbcUrl = "jdbc:postgresql://"
                + LOCALHOST
                + ":"
                + hostPort
                + "/"
                + env.getDatabaseName();

            ctx.getEnvVars().put("SPRING_DATASOURCE_URL", jdbcUrl);
            ctx.getEnvVars().put("SPRING_DATASOURCE_USERNAME", env.getUsername());
            ctx.getEnvVars().put("SPRING_DATASOURCE_PASSWORD", env.getPassword());
        }
        ctx.getEnvVars().put("POSTGRES_HOST", LOCALHOST);
        ctx.getEnvVars().put("POSTGRES_PORT", String.valueOf(hostPort));
    }
}
