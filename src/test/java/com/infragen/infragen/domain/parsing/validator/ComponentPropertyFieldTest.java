package com.infragen.infragen.domain.parsing.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("컴포넌트 속성 필드 정의 검증")
class ComponentPropertyFieldTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("MySQL 필드 목록 — 이미지 버전, 컨테이너 이름, 볼륨 이름, 루트 비밀번호, 사용자 이름, 사용자 비밀번호 순서")
    void of_MySQL_ReturnsFieldsInDeclaredOrder() {
        // when
        List<ComponentPropertyField> fields = ComponentPropertyField.of(ComponentType.MYSQL);

        // then
        assertEquals(
            List.of(
                ComponentPropertyField.MYSQL_IMAGE_VERSION,
                ComponentPropertyField.MYSQL_CONTAINER_NAME,
                ComponentPropertyField.MYSQL_VOLUME_NAME,
                ComponentPropertyField.MYSQL_ROOT_PASSWORD,
                ComponentPropertyField.MYSQL_USERNAME,
                ComponentPropertyField.MYSQL_USER_PASSWORD
            ),
            fields
        );
    }

    @Test
    @DisplayName("PostgreSQL 필드 목록 — 이미지 버전, 컨테이너 이름, 볼륨 이름, 비밀번호, 사용자 이름 순서")
    void of_PostgreSQL_ReturnsFieldsInDeclaredOrder() {
        // when
        List<ComponentPropertyField> fields = ComponentPropertyField.of(ComponentType.POSTGRESQL);

        // then
        assertEquals(
            List.of(
                ComponentPropertyField.POSTGRESQL_IMAGE_VERSION,
                ComponentPropertyField.POSTGRESQL_CONTAINER_NAME,
                ComponentPropertyField.POSTGRESQL_VOLUME_NAME,
                ComponentPropertyField.POSTGRESQL_PASSWORD,
                ComponentPropertyField.POSTGRESQL_USERNAME
            ),
            fields
        );
    }

    @Test
    @DisplayName("MongoDB 필드 목록 — 이미지 버전, 컨테이너 이름, 볼륨 이름, 비밀번호, 사용자 이름 순서")
    void of_MongoDB_ReturnsFieldsInDeclaredOrder() {
        // when
        List<ComponentPropertyField> fields = ComponentPropertyField.of(ComponentType.MONGODB);

        // then
        assertEquals(
            List.of(
                ComponentPropertyField.MONGODB_IMAGE_VERSION,
                ComponentPropertyField.MONGODB_CONTAINER_NAME,
                ComponentPropertyField.MONGODB_VOLUME_NAME,
                ComponentPropertyField.MONGODB_PASSWORD,
                ComponentPropertyField.MONGODB_USERNAME
            ),
            fields
        );
    }

    @Test
    @DisplayName("Redis 필드 목록 — 이미지 버전, 컨테이너 이름, 볼륨 이름, 비밀번호 순서")
    void of_Redis_ReturnsFieldsInDeclaredOrder() {
        // when
        List<ComponentPropertyField> fields = ComponentPropertyField.of(ComponentType.REDIS);

        // then
        assertEquals(
            List.of(
                ComponentPropertyField.REDIS_IMAGE_VERSION,
                ComponentPropertyField.REDIS_CONTAINER_NAME,
                ComponentPropertyField.REDIS_VOLUME_NAME,
                ComponentPropertyField.REDIS_PASSWORD
            ),
            fields
        );
    }

    @Test
    @DisplayName("Caddy 필드 목록 — 이미지 버전, 볼륨 이름 순서이며 컨테이너 이름은 포함하지 않는다")
    void of_Caddy_ReturnsFieldsWithoutContainerName() {
        // when
        List<ComponentPropertyField> fields = ComponentPropertyField.of(ComponentType.CADDY);

        // then
        assertEquals(
            List.of(
                ComponentPropertyField.CADDY_IMAGE_VERSION,
                ComponentPropertyField.CADDY_VOLUME_NAME
            ),
            fields
        );
    }

    @Test
    @DisplayName("null 또는 미지원 타입 필드 목록 — 빈 목록을 반환한다")
    void of_NullOrUnsupported_ReturnsEmptyList() {
        // when & then
        assertTrue(ComponentPropertyField.of(null).isEmpty());
    }

    @Test
    @DisplayName("필드 속성 읽기 — 존재하는 중첩 키와 1레벨 키를 정상 반환한다")
    void read_PresentKeys_ReturnsValue() {
        // given
        JsonNode props = objectMapper.readTree("""
            {
                "imageVersion": "mysql:8.0",
                "containerName": "my-mysql",
                "volumeName": "mysql_data",
                "env": {
                    "rootPassword": "root-password-1",
                    "username": "user1",
                    "userPassword": "user-password-1"
                }
            }
            """);

        // when & then
        assertEquals("mysql:8.0", ComponentPropertyField.MYSQL_IMAGE_VERSION.read(props));
        assertEquals("my-mysql", ComponentPropertyField.MYSQL_CONTAINER_NAME.read(props));
        assertEquals("mysql_data", ComponentPropertyField.MYSQL_VOLUME_NAME.read(props));
        assertEquals("root-password-1", ComponentPropertyField.MYSQL_ROOT_PASSWORD.read(props));
        assertEquals("user1", ComponentPropertyField.MYSQL_USERNAME.read(props));
        assertEquals("user-password-1", ComponentPropertyField.MYSQL_USER_PASSWORD.read(props));
    }

    @Test
    @DisplayName("필드 속성 읽기 — 없는 키이거나 props가 null이면 빈 문자열을 반환한다")
    void read_MissingKeyOrNullNode_ReturnsEmptyString() {
        // given
        JsonNode props = objectMapper.readTree("{}");

        // when & then
        assertEquals("", ComponentPropertyField.MYSQL_IMAGE_VERSION.read(props));
        assertEquals("", ComponentPropertyField.MYSQL_ROOT_PASSWORD.read(props));
        assertEquals("", ComponentPropertyField.MYSQL_IMAGE_VERSION.read(null));
    }

    @Test
    @DisplayName("필드 메타데이터 — kind, errorCode, componentType이 일관되게 정의되어 있다")
    void fieldMetadata_Consistent() {
        // when & then
        assertEquals(ComponentType.MYSQL, ComponentPropertyField.MYSQL_IMAGE_VERSION.componentType());
        assertEquals(PropertyFieldKind.IMAGE_VERSION, ComponentPropertyField.MYSQL_IMAGE_VERSION.kind());
        assertEquals(ParsingErrorCode.INVALID_MYSQL_IMAGE_VERSION, ComponentPropertyField.MYSQL_IMAGE_VERSION.errorCode());

        assertEquals(ComponentType.REDIS, ComponentPropertyField.REDIS_PASSWORD.componentType());
        assertEquals(PropertyFieldKind.ACCOUNT, ComponentPropertyField.REDIS_PASSWORD.kind());
        assertEquals(ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER, ComponentPropertyField.REDIS_PASSWORD.errorCode());
    }
}
