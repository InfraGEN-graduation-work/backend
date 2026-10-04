package com.infragen.infragen.domain.generation.generator.application;

/**
 * DB 하나의 접속 정보를 구성요소 단위로 담는다.
 *
 * <p>DB 부품은 접속 문자열을 조립하지 않고 이 구성요소만 낸다. JDBC URL, MongoDB URI처럼 앱 타입마다
 * 다른 형식은 {@link ApplicationEnvMapper}가 이 값으로 조립한다. 값은 따옴표 없는 평문이고, CLOUD의
 * {@code ${...}} 참조식도 문자열 그대로 담는다. YAML 따옴표는 출력하는 쪽이 감싼다.
 *
 * @param scheme DB 종류를 나타내는 URL scheme. 예: {@code mysql}, {@code postgresql}
 * @param jdbc JDBC로 접속하는 DB인지 여부. DB 부품이 선언하며 앱 매퍼의 DataSource 개수 판단 기준이다.
 */
public record DatabaseConnection(
    String scheme,
    boolean jdbc,
    String host,
    String port,
    String database,
    String username,
    String password
) {
}
