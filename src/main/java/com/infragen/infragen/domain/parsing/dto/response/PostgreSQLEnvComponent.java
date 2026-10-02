package com.infragen.infragen.domain.parsing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PostgreSQL 노드의 DB 접속 정보다.
 *
 * <p>PostgreSQL 공식 이미지는 superuser를 {@code POSTGRES_USER}로 만들므로 MySQL과 달리 root 비밀번호를 따로 두지 않는다.
 * parser가 필수 값과 형식 검증을 마친 값만 담는다.
 */
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PostgreSQLEnvComponent {
    private String databaseName;
    private String username;
    private String password;
}
