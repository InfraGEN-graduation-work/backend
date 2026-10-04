package com.infragen.infragen.domain.generation.generator.cloud;

import com.infragen.infragen.domain.generation.generator.application.DatabaseConnection;
import com.infragen.infragen.global.enums.ComponentType;
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
     * <p>default를 두지 않아 새 의존 인프라 renderer가 중립 변수를 빠뜨리면 컴파일되지 않는다.
     *
     * @return 변수 이름과 YAML 스칼라 원문
     */
    SequencedMap<String, String> applicationEnvironment();

    /**
     * 앱 타입별 {@code ApplicationEnvMapper}가 프레임워크 변수로 매핑할 DB 접속 정보를 구성요소로 만든다.
     *
     * <p>단일 DB 여부 판단과 프레임워크 변수 생성은 앱 매퍼가 맡고, DB 부품은 관계형 여부만 선언한다.
     * 값은 따옴표 없는 평문이다. DB 이름, 계정, 비밀번호는 외부 {@code .env} 참조식({@code ${...}}) 그대로 담고,
     * YAML 따옴표는 출력하는 쪽이 감싼다. {@link #isEnabled}가 {@code true}일 때만 호출한다.
     *
     * @return 앱 컨테이너가 Compose 서비스 DNS로 접속할 정보. DB가 아니면 빈 값
     */
    default Optional<DatabaseConnection> databaseConnection() {
        return Optional.empty();
    }
}
