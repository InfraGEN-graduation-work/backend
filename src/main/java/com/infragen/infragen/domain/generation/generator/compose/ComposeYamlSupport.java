package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import java.util.Locale;
import java.util.Map;

// Docker Compose / .env 생성 시 renderer 공통 유틸 전용 클래스
public final class ComposeYamlSupport {

    private ComposeYamlSupport() {
    }

    /**
     * containerName -> name(또는 라벨) -> 타입 표시명 순으로 compose service 키 생성
     */
    public static String toServiceName(String containerName, String nameOrLabel, String typeFallback) {
        String raw = firstNonBlank(containerName, nameOrLabel, typeFallback);
        String lower = raw.toLowerCase(Locale.ROOT);
        String normalized = lower.replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        return normalized;
    }

    /**
     * containerName이 비어 있으면 Compose service 키를 container_name으로 쓴다.
     */
    public static String resolveContainerName(String containerName, String serviceName) {
        if (containerName != null && !containerName.isBlank()) {
            return containerName.trim();
        }
        return serviceName;
    }

    /**
     * imageVersion이 비어 있으면 renderer별 기본 이미지를 쓴다.
     */
    public static String resolveImage(String imageVersion, String defaultImage) {
        if (imageVersion != null && !imageVersion.isBlank()) {
            return imageVersion.trim();
        }
        return defaultImage;
    }

    /**
     * 사용자 입력에서 온 이름을 Compose YAML의 큰따옴표 문자열로 만든다.
     *
     * <p>따옴표 없이 쓰면 YAML이 {@code true}, {@code 123}, {@code null}을 불리언, 숫자, null로 읽어 Compose가 거부한다.
     * 파서가 형식을 검증해 {@code "}, 백슬래시, 줄바꿈이 없다는 전제로 이스케이프하지 않고 감싼다.
     *
     * @throws IaCGenerationException 전제를 어긴 값이 renderer에 직접 전달된 경우
     */
    public static String toYamlString(String value) {
        // parser를 거치지 않고 호출돼도 YAML 구조가 바뀌지 않도록 내부 계약 위반으로 거부한다.
        if (value == null || value.chars().anyMatch(c -> c == '"' || c == '\\' || c == '\n' || c == '\r' || c == '\0')) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }
        return "\"" + value + "\"";
    }

    // .env 값 직렬화 — 공백, #, 작은따옴표, 백틱이 있으면 큰따옴표로 감싼다
    /**
     * {@code .env} 한 줄의 값을 Compose와 Node.js가 같게 읽도록 직렬화한다.
     *
     * <p>공백, {@code #}, 작은따옴표, 백틱이 있으면 큰따옴표로 감싸고 그대로 쓴다. {@code "}, 백슬래시, {@code $}는
     * 이스케이프하지 않고 거부한다. 이스케이프 규칙이 Compose 전용이라 Node.js가 값을 다르게 읽기 때문이다.
     *
     * @throws IaCGenerationException 값에 큰따옴표, 백슬래시, 달러 기호가 있는 경우. 값은 메시지에 노출하지 않는다.
     */
    public static String escapeEnvValue(String value) {
        if (value == null) {
            return "";
        }
        if (containsUnsupportedEnvCharacter(value)) {
            throw new IaCGenerationException(IaCGenerationErrorCode.UNSUPPORTED_ENV_CHARACTER);
        }
        if (!needsQuoting(value)) {
            return value;
        }
        return "\"" + value + "\"";
    }

    /**
     * context.envVars를 {@code .env} 파일 본문으로 바꾼다.
     *
     * @throws IaCGenerationException 값에 줄바꿈이나 NUL이 있거나, 허용되지 않는 문자가 있어 한 줄 형식으로 표현할 수 없는 경우
     */
    public static String formatEnvFile(Map<String, String> envVars) {
        if (envVars == null || envVars.isEmpty()) {
            return "# InfraGEN generated\n";
        }

        StringBuilder content = new StringBuilder();
        content.append("# InfraGEN generated environment variables\n");
        content.append("# 민감한 정보는 이 파일에만 저장하세요. 버전 관리에 커밋하지 마세요.\n");
        content.append('\n');

        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            // 개행은 다음 줄을 별도 변수로 만들거나 줄 단위 소비자를 깨뜨린다. 값은 오류 메시지에 노출하지 않는다.
            if (containsLineBreakOrNul(entry.getValue())) {
                throw new IaCGenerationException(IaCGenerationErrorCode.UNSUPPORTED_ENV_VALUE);
            }
            content.append(entry.getKey())
                    .append('=')
                    .append(escapeEnvValue(entry.getValue()))
                    .append('\n');
        }

        if (content.charAt(content.length() - 1) == '\n') {
            content.setLength(content.length() - 1);
        }
        content.append('\n');
        return content.toString();
    }

    private static boolean containsLineBreakOrNul(String value) {
        return value != null && value.chars().anyMatch(c -> c == '\n' || c == '\r' || c == '\0');
    }

    private static boolean containsUnsupportedEnvCharacter(String value) {
        return value.chars().anyMatch(c -> c == '"' || c == '\\' || c == '$');
    }

    private static boolean needsQuoting(String value) {
        return value.chars().anyMatch(c -> " #'`".indexOf(c) >= 0);
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "";
    }
}
