package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;

// LOCAL_DEV — 호스트에서 실행할 애플리케이션용 .env 키와 값을 context에 추가
public interface HostAppEnvContributor {

    ComponentType getDependencyType();

    /**
     * 호스트 실행 앱의 {@code .env}에 넣을 이 의존 인프라의 타입별 중립 변수를 만든다.
     *
     * <p>프레임워크와 무관하게 연결된 의존 인프라마다 항상 만드는 변수다. Spring 전용 변수는 만들지 않는다.
     * 값은 {@code .env}에 그대로 쓰는 실제 값이고, map 순서대로 출력된다.
     *
     * @param dependency 이 contributor의 {@link #getDependencyType()}에 해당하는 의존 컴포넌트
     * @return 변수 이름과 값. 제공할 변수가 없으면 빈 map
     */
    default SequencedMap<String, String> hostAppEnvironment(BaseComponent dependency) {
        return new LinkedHashMap<>();
    }

    /**
     * Spring {@code SPRING_DATASOURCE_*}로 매핑할 JDBC 연결 정보를 만든다.
     *
     * <p>단일 DB 여부 판단과 Spring 변수 생성은 호출하는 쪽이 맡는다.
     *
     * @param dependency 이 contributor의 {@link #getDependencyType()}에 해당하는 의존 컴포넌트
     * @return JDBC 연결 정보. JDBC DataSource 대상이 아니면 빈 값
     */
    default Optional<JdbcConnection> jdbcConnection(BaseComponent dependency) {
        return Optional.empty();
    }

    /**
     * 애플리케이션 하나에 연결된 의존 인프라 하나의 접속 변수를 {@code .env}에 추가한다.
     *
     * <p>앱의 DATABASE 의존이 하나일 때만 {@link #jdbcConnection}으로 {@code SPRING_DATASOURCE_*}를 먼저 넣고,
     * 이어서 {@link #hostAppEnvironment}를 넣는다. DB가 둘 이상이면 기본 DataSource를 정할 수 없기 때문이다.
     */
    default void contributeHostAppEnv(
        BaseComponent dependency,
        BaseComponent application,
        ComposeGenerationContext ctx
    ) {
        if (ctx.hasSingleDatabaseDependency(application.getNodeId())) {
            jdbcConnection(dependency).ifPresent(connection -> {
                ctx.getEnvVars().put("SPRING_DATASOURCE_URL", connection.url());
                ctx.getEnvVars().put("SPRING_DATASOURCE_USERNAME", connection.username());
                ctx.getEnvVars().put("SPRING_DATASOURCE_PASSWORD", connection.password());
            });
        }
        ctx.getEnvVars().putAll(hostAppEnvironment(dependency));
    }

    /** 호스트 실행 앱이 {@code localhost}와 사용자 입력 호스트 포트로 접속할 JDBC 연결 정보다. */
    record JdbcConnection(String url, String username, String password) {
    }
}
