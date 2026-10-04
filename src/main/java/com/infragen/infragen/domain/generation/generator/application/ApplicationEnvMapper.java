package com.infragen.infragen.domain.generation.generator.application;

import java.util.Optional;
import java.util.SequencedMap;

import com.infragen.infragen.global.enums.ComponentType;

/**
 * 애플리케이션 타입별로 의존 인프라의 접속 정보를 그 프레임워크의 환경변수로 매핑한다.
 *
 * <p>DB와 캐시 부품은 프레임워크를 모른 채 타입별 중립 변수만 만든다. 프레임워크 전용 변수는 이 매퍼가
 * 만들고, LOCAL_DEV generator와 CLOUD_DEPLOY renderer가 같은 규칙을 쓰도록 한곳에 둔다.
 * 계열별 기본 DB를 정하는 판단과 안내 문구는 이 매퍼가 맡고, 값 출력 형식(YAML 따옴표 등)은 호출하는 쪽이 맡는다.
 */
public interface ApplicationEnvMapper {

    ComponentType getApplicationType();

    /**
     * 앱에 연결된 DB 접속 정보를 프레임워크 변수로 매핑한다.
     *
     * <p>이 앱 타입이 DataSource로 쓸 수 있는 DB가 정확히 하나일 때만 변수를 만든다. 둘 이상이면 어느
     * DB를 쓸지 정할 수 없어 빈 map을 돌려준다.
     *
     * <p>관계형이 아닌 DB는 종류({@link ComponentType})별 규칙으로 매핑하며, 규칙이 없는 종류는 조용히 빠지지 않도록
     * {@code IaCGenerationException}으로 거부한다.
     *
     * @param connections 앱에 연결된 DB의 접속 정보. 키는 그 연결을 낸 노드의 종류이고, 같은 타입 DB 중복은
     *     parsing에서 거부되므로 종류마다 하나다.
     * @return 변수 이름과 값. 출력은 map 순서를 따른다.
     */
    SequencedMap<String, String> databaseEnvironment(SequencedMap<ComponentType, DatabaseConnection> connections);

    /**
     * {@link #databaseEnvironment}가 DB가 여러 개라 변수를 만들지 않았을 때의 Compose 안내 주석을 만든다.
     *
     * @param connections {@link #databaseEnvironment}와 같은 연결 정보
     * @param envSource 안내 문구가 가리킬 접속 변수의 위치 이름. 예: {@code .env}
     * @return 안내가 필요 없으면 빈 값
     */
    Optional<String> multipleDatabaseNotice(
        SequencedMap<ComponentType, DatabaseConnection> connections,
        String envSource
    );
}
