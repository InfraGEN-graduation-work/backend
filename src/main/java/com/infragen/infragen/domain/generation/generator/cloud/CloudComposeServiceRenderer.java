package com.infragen.infragen.domain.generation.generator.cloud;

import com.infragen.infragen.global.enums.ComponentType;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;

/** CLOUD_DEPLOY Compose에 하나의 의존 인프라 서비스를 렌더링하는 계약이다. */
public interface CloudComposeServiceRenderer {

    /** @return 이 renderer가 담당하는 runtime component type */
    ComponentType getSupportedType();

    /** @return Compose service key */
    String getServiceName();

    /** @param context CLOUD_DEPLOY renderer가 공유하는 입력 정보 */
    boolean isEnabled(CloudDeployContext context);

    /** @param context CLOUD_DEPLOY renderer가 공유하는 입력 정보 */
    boolean isDependency(CloudDeployContext context);

    /** @param context CLOUD_DEPLOY renderer가 공유하는 입력 정보
     * @return Compose service block
     */
    String render(CloudDeployContext context);

    /**
     * 앱 컨테이너 {@code environment}에 넣을 이 의존 인프라의 타입별 중립 변수를 만든다.
     *
     * <p>프레임워크와 무관하게 연결된 의존 인프라마다 항상 만드는 변수다. Spring 전용 변수는 만들지 않는다.
     * 값은 따옴표를 포함한 YAML 스칼라 원문이고, map 순서대로 출력된다.
     * {@link #isEnabled}가 {@code true}일 때만 호출한다.
     *
     * @return 변수 이름과 YAML 스칼라 원문. 제공할 변수가 없으면 빈 map
     */
    default SequencedMap<String, String> applicationEnvironment() {
        return new LinkedHashMap<>();
    }

    /**
     * Spring {@code SPRING_DATASOURCE_*}로 매핑할 JDBC 연결 정보를 만든다.
     *
     * <p>단일 DB 여부 판단과 Spring 변수 생성은 호출하는 쪽이 맡는다.
     * {@link #isEnabled}가 {@code true}일 때만 호출한다.
     *
     * @return JDBC 연결 정보. JDBC DataSource 대상이 아니면 빈 값
     */
    default Optional<JdbcConnection> jdbcConnection() {
        return Optional.empty();
    }

    /**
     * 앱 컨테이너가 Compose 서비스 DNS로 접속할 JDBC 연결 정보다.
     *
     * <p>각 값은 따옴표를 포함한 YAML 스칼라 원문이며, 비밀값은 외부 {@code .env} 참조식으로 둔다.
     */
    record JdbcConnection(String url, String username, String password) {
    }
}
