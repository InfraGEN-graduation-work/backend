package com.infragen.infragen.domain.parsing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MongoDB 노드의 DB 접속 정보다.
 *
 * <p>MongoDB 공식 이미지는 root 계정을 {@code MONGO_INITDB_ROOT_USERNAME/PASSWORD}로 만들고 앱이 이 계정을 그대로 쓴다.
 * 그래서 앱 전용 계정이나 root 비밀번호를 따로 두지 않는다. parser가 필수 값과 형식 검증을 마친 값만 담는다.
 */
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class MongoDBEnvComponent {
    private String databaseName;
    private String username;
    private String password;
}
