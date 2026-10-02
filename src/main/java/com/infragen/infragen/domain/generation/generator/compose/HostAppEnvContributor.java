package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.Optional;
import java.util.SequencedMap;

// LOCAL_DEV — 호스트에서 실행할 애플리케이션이 의존 인프라에 접속할 .env 값을 제공한다.
// 앱 타입별 프레임워크 매핑은 DockerComposeIaCGenerator가 ApplicationEnvMapper로 처리한다.
public interface HostAppEnvContributor {

    ComponentType getDependencyType();

    /**
     * 호스트 실행 앱의 {@code .env}에 넣을 이 의존 인프라의 타입별 중립 변수를 만든다.
     *
     * <p>프레임워크와 무관하게 연결된 의존 인프라마다 항상 만드는 변수다. 프레임워크 전용 변수는 만들지 않는다.
     * 값은 {@code .env}에 그대로 쓰는 실제 값이고, map 순서대로 출력된다.
     *
     * <p>default를 두지 않아 새 의존 인프라 contributor가 중립 변수를 빠뜨리면 컴파일되지 않는다.
     *
     * @param dependency 이 contributor의 {@link #getDependencyType()}에 해당하는 의존 컴포넌트
     * @return 변수 이름과 값
     */
    SequencedMap<String, String> hostAppEnvironment(BaseComponent dependency);

    /**
     * 앱 타입별 {@code ApplicationEnvMapper}가 DataSource 변수로 매핑할 JDBC 연결 정보를 만든다.
     *
     * <p>단일 DB 여부 판단과 프레임워크 변수 생성은 호출하는 쪽이 맡는다.
     *
     * @param dependency 이 contributor의 {@link #getDependencyType()}에 해당하는 의존 컴포넌트
     * @return JDBC 연결 정보. JDBC DataSource 대상이 아니면 빈 값
     */
    default Optional<JdbcConnection> jdbcConnection(BaseComponent dependency) {
        return Optional.empty();
    }

    /** 호스트 실행 앱이 {@code localhost}와 사용자 입력 호스트 포트로 접속할 JDBC 연결 정보다. */
    record JdbcConnection(String url, String username, String password) {
    }
}
