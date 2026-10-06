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

    // .env 값 이스케이프 — 공백 또는 # 포함 시 따옴표, 내부 " 는 \"
    /**
     * {@code .env} 한 줄의 값을 Compose가 입력 그대로 읽도록 직렬화한다.
     *
     * <p>공백, {@code #}, {@code $}, 따옴표, 백슬래시, 백틱이 있으면 큰따옴표로 감싸고
     * {@code \}, {@code "}, {@code $}를 이스케이프한다. 없으면 그대로 출력한다.
     * 개행 계열 값의 처리는 이 메서드의 책임이 아니다.
     */
    public static String escapeEnvValue(String value) {
        if (value == null) {
            return "";
        }
        if (!needsQuoting(value)) {
            return value;
        }
        // 작은따옴표는 끝이 백슬래시인 값과 \' 조합을 표현하지 못해 큰따옴표를 쓴다.
        // $는 Compose 보간이라 $$로 써야 리터럴이 된다.
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "$$") + "\"";
    }

    /**
     * context.envVars를 {@code .env} 파일 본문으로 바꾼다.
     *
     * @throws IaCGenerationException 값에 줄바꿈이나 NUL이 있어 한 줄 형식으로 표현할 수 없는 경우
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

    private static boolean needsQuoting(String value) {
        return value.chars().anyMatch(c -> " #$'\"\\`".indexOf(c) >= 0);
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
