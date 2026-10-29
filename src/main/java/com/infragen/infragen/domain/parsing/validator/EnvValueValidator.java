package com.infragen.infragen.domain.parsing.validator;

import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;

/**
 * LOCAL_DEV {@code .env}에 쓰이는 사용자 이름과 비밀번호의 금지 문자를 파싱 단계에서 거부한다.
 *
 * <p>Docker Compose와 Node.js가 {@code $}, {@code \}, {@code "}를 다르게 해석해서 이스케이프하지 않고 거부한다.
 * 생성 단계의 {@code ComposeYamlSupport} 검사와 같은 문자 집합을 쓰고, 그 검사는 마지막 방어선으로 남긴다. 파서마다 문자 목록이 어긋나지 않도록 한곳에 둔다.
 */
public final class EnvValueValidator {

    private EnvValueValidator() {
    }

    /**
     * 값에 금지 문자({@code "}, {@code \}, {@code $}, 줄바꿈, NUL)가 있으면 예외를 던진다.
     *
     * <p>null과 빈 값은 통과시킨다. 누락과 길이 검사는 각 파서가 먼저 처리하므로 호출자는 그 검사 뒤에 호출해야 한다.
     *
     * @throws ParsingException 금지 문자가 있는 경우. 값과 필드 이름은 노출하지 않는다.
     */
    public static void validate(String value) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            if (isForbidden(value.charAt(i))) {
                throw new ParsingException(ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER);
            }
        }
    }

    /**
     * 컴포넌트 타입의 계정 값 필드를 모두 읽어 금지 문자가 있으면 예외를 던진다.
     *
     * <p>필수값 누락과 길이는 보지 않아 미완성 노드도 통과한다. 파서는 필드마다 누락 검사 뒤에 검사해야 입력이 여러 곳 틀렸을 때 오류 순서가 유지되므로 이 메서드 대신
     * {@link #validate}를 직접 호출한다. 검사 대상이 없는 타입은 통과한다.
     *
     * @throws ParsingException 금지 문자가 있는 필드를 처음 만난 경우. 값과 필드 이름은 노출하지 않는다.
     */
    public static void validateProperties(ComponentType componentType, JsonNode props) {
        for (EnvValueField field : EnvValueField.of(componentType)) {
            validate(field.read(props));
        }
    }

    private static boolean isForbidden(char c) {
        return c == '"' || c == '\\' || c == '$' || c == '\n' || c == '\r' || c == '\0';
    }
}
