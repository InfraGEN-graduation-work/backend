package com.infragen.infragen.domain.parsing.validator;

import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;

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
        if (hasForbiddenCharacter(value)) {
            throw new ParsingException(ParsingErrorCode.UNSUPPORTED_ENV_VALUE_CHARACTER);
        }
    }

    /**
     * 값에 금지 문자({@code "}, {@code \}, {@code $}, 줄바꿈, NUL)가 포함되어 있는지 검사한다.
     *
     * <p>null인 경우 금지 문자가 없는 것으로 간주하여 {@code false}를 반환한다.
     *
     * @param value 검사할 문자열
     * @return 금지 문자가 포함되어 있으면 {@code true}, 그렇지 않으면 {@code false}
     */
    public static boolean hasForbiddenCharacter(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (isForbidden(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isForbidden(char c) {
        return c == '"' || c == '\\' || c == '$' || c == '\n' || c == '\r' || c == '\0';
    }
}
