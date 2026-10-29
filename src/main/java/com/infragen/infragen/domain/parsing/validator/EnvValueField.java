package com.infragen.infragen.domain.parsing.validator;

import java.util.Arrays;
import java.util.List;

import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;

/**
 * 노드 {@code properties}에서 {@code .env} 금지 문자 검사 대상인 사용자 이름과 비밀번호의 위치를 한곳에 정의한다.
 *
 * <p>컴포넌트 파서와 {@link EnvValueValidator#validateProperties}가 같은 위치에서 값을 읽게 하려는 것이다. 위치를 각자 적으면 한쪽에만 키가 추가돼
 * 생성 요청은 거부하는데 다른 경로는 통과시키는 어긋남이 생긴다. 파서가 새 계정 값을 검사하기 시작하면 여기에 먼저 추가한다.
 */
public enum EnvValueField {

    MYSQL_ROOT_PASSWORD(ComponentType.MYSQL, "env", "rootPassword"),
    MYSQL_USERNAME(ComponentType.MYSQL, "env", "username"),
    MYSQL_USER_PASSWORD(ComponentType.MYSQL, "env", "userPassword"),
    POSTGRESQL_PASSWORD(ComponentType.POSTGRESQL, "env", "password"),
    POSTGRESQL_USERNAME(ComponentType.POSTGRESQL, "env", "username"),
    MONGODB_PASSWORD(ComponentType.MONGODB, "env", "password"),
    MONGODB_USERNAME(ComponentType.MONGODB, "env", "username"),
    REDIS_PASSWORD(ComponentType.REDIS, "password");

    private final ComponentType componentType;
    private final String[] path;

    EnvValueField(ComponentType componentType, String... path) {
        this.componentType = componentType;
        this.path = path;
    }

    public ComponentType componentType() {
        return componentType;
    }

    /**
     * {@code properties}에서 이 필드의 값을 문자열로 읽는다.
     *
     * <p>없는 키는 빈 문자열이 된다. 파서가 쓰던 {@code path(...).asString()}과 같은 변환이라 기존 누락, 길이 검사의 입력이 바뀌지 않는다.
     */
    public String read(JsonNode props) {
        JsonNode node = props;
        for (String key : path) {
            node = node.path(key);
        }
        return node.asString();
    }

    /**
     * 컴포넌트 타입이 가진 검사 대상 필드를 선언 순서대로 돌려준다. 대상이 없는 타입은 빈 목록이다.
     */
    public static List<EnvValueField> of(ComponentType componentType) {
        return Arrays.stream(values())
            .filter(field -> field.componentType == componentType)
            .toList();
    }
}
