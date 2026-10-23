package com.infragen.infragen.domain.parsing.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.MongoDBComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.ObjectMapper;

@DisplayName("MongoDB 파서")
class MongoDBParserTest {

    private final MongoDBParser mongoDBParser = new MongoDBParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("유효한 MongoDB 노드 — 속성과 접속 정보 파싱 성공")
    void parse_ValidNode_ReturnsMongoDBComponent() {
        // given
        Map<String, Object> properties = validProperties();

        // when
        MongoDBComponent component = (MongoDBComponent) parse(properties);

        // then
        assertAll(
            () -> assertEquals("node-1", component.getNodeId()),
            () -> assertEquals(ComponentType.MONGODB, component.getComponentType()),
            () -> assertEquals("mongo:8.0", component.getImageVersion()),
            () -> assertEquals("mongo", component.getContainerName()),
            () -> assertEquals("mongo_data", component.getVolumeName()),
            () -> assertEquals(27017, component.getPort()),
            () -> assertEquals("appdb", component.getEnv().getDatabaseName()),
            () -> assertEquals("appuser", component.getEnv().getUsername()),
            () -> assertEquals("password12", component.getEnv().getPassword())
        );
    }

    @Test
    @DisplayName("선택 속성 누락 — containerName, volumeName 빈 문자열")
    void parse_MissingOptionalProperties_UsesEmptyString() {
        // given
        Map<String, Object> properties = validProperties();
        properties.remove("containerName");
        properties.remove("volumeName");

        // when
        MongoDBComponent component = (MongoDBComponent) parse(properties);

        // then
        assertAll(
            () -> assertEquals("", component.getContainerName()),
            () -> assertEquals("", component.getVolumeName())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "app-db", "app db"})
    @DisplayName("databaseName 형식 오류 — INVALID_DB_NAME")
    void parse_InvalidDatabaseName_ThrowsParsingException(String databaseName) {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).put("databaseName", databaseName);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.INVALID_DB_NAME, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short12"})
    @DisplayName("password 8자 미만 — INVALID_DB_PASSWORD")
    void parse_ShortPassword_ThrowsParsingException(String password) {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).put("password", password);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.INVALID_DB_PASSWORD, exception.getCode());
    }

    @Test
    @DisplayName("password 누락 — INVALID_DB_PASSWORD")
    void parse_MissingPassword_ThrowsParsingException() {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).remove("password");

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.INVALID_DB_PASSWORD, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("imageVersion 빈 값 — MISSING_MONGODB_IMAGE_VERSION")
    void parse_BlankImageVersion_ThrowsParsingException(String imageVersion) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("imageVersion", imageVersion);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.MISSING_MONGODB_IMAGE_VERSION, exception.getCode());
    }

    @Test
    @DisplayName("imageVersion 누락 — MISSING_MONGODB_IMAGE_VERSION")
    void parse_MissingImageVersion_ThrowsParsingException() {
        // given
        Map<String, Object> properties = validProperties();
        properties.remove("imageVersion");

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.MISSING_MONGODB_IMAGE_VERSION, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("username 빈 값 — MISSING_MONGODB_USERNAME")
    void parse_BlankUsername_ThrowsParsingException(String username) {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).put("username", username);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(ParsingErrorCode.MISSING_MONGODB_USERNAME, exception.getCode());
    }

    @Test
    @DisplayName("imageVersion 누락 — 사용자에게 보이는 메시지가 MongoDB 이미지 버전 누락을 알린다")
    void parse_MissingImageVersion_ReturnsReadableMessage() {
        // given
        Map<String, Object> properties = validProperties();
        properties.remove("imageVersion");

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals("MongoDB 이미지 버전이 누락되었습니다.", exception.getMessage());
    }

    @Test
    @DisplayName("username 누락 — 사용자에게 보이는 메시지가 MongoDB 사용자 이름 누락을 알린다")
    void parse_MissingUsername_ReturnsReadableMessage() {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).remove("username");

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals("MongoDB 사용자 이름이 누락되었습니다.", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\"b", "a\\b", "a$b", "a\nb", "a\rb", "a\0b"})
    @DisplayName("username 금지 문자 — UNSUPPORTED_ENV_VALUE_CHARACTER")
    void parse_UsernameWithForbiddenCharacter_ThrowsParsingException(String value) {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).put("username", value);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(
            ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcd\"efgh", "abcd\\efgh", "abcd$efgh", "abcd\nefgh", "abcd\refgh", "abcd\0efgh"})
    @DisplayName("password 금지 문자 — UNSUPPORTED_ENV_VALUE_CHARACTER")
    void parse_PasswordWithForbiddenCharacter_ThrowsParsingException(String value) {
        // given
        Map<String, Object> properties = validProperties();
        env(properties).put("password", value);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(
            ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "mongo", "mongo:8.0-alpine", "ghcr.io/org/mongo:8.0", "localhost:5000/mongo:8.0",
        "mongo@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    })
    @DisplayName("Docker 이미지 참조 형식 — 파싱 성공")
    void parse_ValidImageVersion_ReturnsComponent(String imageVersion) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("imageVersion", imageVersion);

        // when
        MongoDBComponent component = (MongoDBComponent) parse(properties);

        // then
        assertEquals(imageVersion, component.getImageVersion());
    }

    @Test
    @DisplayName("imageVersion 앞뒤 공백 — 제거해서 저장")
    void parse_ImageVersionWithSurroundingSpaces_TrimsImageVersion() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("imageVersion", " mongo:8.0 ");

        // when
        MongoDBComponent component = (MongoDBComponent) parse(properties);

        // then
        assertEquals("mongo:8.0", component.getImageVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "mongo:8.0\n    privileged: true", "mongo 8.0", "${IMAGE}", "mongo:",
        "mongo::8.0", "Mongo:8.0", "mongo@sha256:abc123"
    })
    @DisplayName("YAML 구조를 바꾸거나 이미지 참조 구조가 아닌 imageVersion — INVALID_MONGODB_IMAGE_VERSION")
    void parse_InvalidImageVersion_ThrowsParsingException(String imageVersion) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("imageVersion", imageVersion);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(
            ParsingErrorCode.INVALID_MONGODB_IMAGE_VERSION,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"mongo_data", "mongo-data.1", "Data1"})
    @DisplayName("Docker 볼륨 이름 규칙 — 파싱 성공")
    void parse_ValidVolumeName_ReturnsComponent(String volumeName) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("volumeName", volumeName);

        // when
        MongoDBComponent component = (MongoDBComponent) parse(properties);

        // then
        assertEquals(volumeName, component.getVolumeName());
    }

    @Test
    @DisplayName("volumeName 앞뒤 공백 — 제거해서 저장")
    void parse_VolumeNameWithSurroundingSpaces_TrimsVolumeName() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("volumeName", "  mongo_data  ");

        // when
        MongoDBComponent component = (MongoDBComponent) parse(properties);

        // then
        assertEquals("mongo_data", component.getVolumeName());
    }

    @Test
    @DisplayName("volumeName 공백뿐인 값 — 볼륨 없이 파싱")
    void parse_BlankVolumeName_ReturnsEmptyVolumeName() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("volumeName", "  ");

        // when
        MongoDBComponent component = (MongoDBComponent) parse(properties);

        // then
        assertEquals("", component.getVolumeName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mongo@data", "data:/etc", "mongo data", "mongo\ndata", "_data", "${VOL}"})
    @DisplayName("볼륨 이름 규칙을 어긴 volumeName — INVALID_MONGODB_VOLUME_NAME")
    void parse_InvalidVolumeName_ThrowsParsingException(String volumeName) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("volumeName", volumeName);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(
            ParsingErrorCode.INVALID_MONGODB_VOLUME_NAME,
            exception.getCode()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"mongo-db", "mongo_db.1", "DB1"})
    @DisplayName("Docker 컨테이너 이름 규칙 — 파싱 성공")
    void parse_ValidContainerName_ReturnsComponent(String containerName) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("containerName", containerName);

        // when
        MongoDBComponent component = (MongoDBComponent) parse(properties);

        // then
        assertEquals(containerName, component.getContainerName());
    }

    @Test
    @DisplayName("containerName 앞뒤 공백 — 제거해서 저장")
    void parse_ContainerNameWithSurroundingSpaces_TrimsContainerName() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("containerName", "  mongo-db  ");

        // when
        MongoDBComponent component = (MongoDBComponent) parse(properties);

        // then
        assertEquals("mongo-db", component.getContainerName());
    }

    @Test
    @DisplayName("containerName 공백뿐인 값 — 빈 값으로 파싱")
    void parse_BlankContainerName_ReturnsEmptyContainerName() {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("containerName", "  ");

        // when
        MongoDBComponent component = (MongoDBComponent) parse(properties);

        // then
        assertEquals("", component.getContainerName());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "mongo\n    privileged: true", "mongo db", "mongo:1", "mongo/1", "${NAME}", "_mongo", "a"
    })
    @DisplayName("컨테이너 이름 규칙을 어긴 containerName — INVALID_MONGODB_CONTAINER_NAME")
    void parse_InvalidContainerName_ThrowsParsingException(String containerName) {
        // given
        Map<String, Object> properties = validProperties();
        properties.put("containerName", containerName);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parse(properties)
        );

        // then
        assertEquals(
            ParsingErrorCode.INVALID_MONGODB_CONTAINER_NAME,
            exception.getCode()
        );
    }

    private BaseComponent parse(Map<String, Object> properties) {
        NodeDTO node = new NodeDTO(
            "node-1",
            "MONGODB",
            100f,
            200f,
            properties
        );

        return mongoDBParser.parse(
            node,
            objectMapper.valueToTree(properties),
            27017
        );
    }

    private static Map<String, Object> validProperties() {
        Map<String, Object> env = new HashMap<>();
        env.put("databaseName", "appdb");
        env.put("username", "appuser");
        env.put("password", "password12");

        Map<String, Object> properties = new HashMap<>();
        properties.put("imageVersion", "mongo:8.0");
        properties.put("containerName", "mongo");
        properties.put("volumeName", "mongo_data");
        properties.put("port", 27017);
        properties.put("env", env);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> env(
        Map<String, Object> properties
    ) {
        return (Map<String, Object>) properties.get("env");
    }
}
