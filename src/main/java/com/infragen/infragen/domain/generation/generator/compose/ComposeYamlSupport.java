package com.infragen.infragen.domain.generation.generator.compose;

import java.util.Locale;
import java.util.Map;

// Docker Compose / .env 생성 시 renderer 공통 유틸 전용 클래스
public final class ComposeYamlSupport {

    private ComposeYamlSupport() {
    }

    // containerName -> name(또는 라벨) -> 타입 표시명 순으로 compose service 키 생성
    public static String toServiceName(String containerName, String nameOrLabel, String typeFallback) {
        String raw = firstNonBlank(containerName, nameOrLabel, typeFallback);
        String lower = raw.toLowerCase(Locale.ROOT);
        String normalized = lower.replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        return normalized;
    }

    // .env 값 이스케이프 — 공백 또는 # 포함 시 따옴표, 내부 " 는 \"
    public static String escapeEnvValue(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(" ") || value.contains("#")) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }

    // context.envVars -> .env 파일 본문
    public static String formatEnvFile(Map<String, String> envVars) {
        if (envVars == null || envVars.isEmpty()) {
            return "# InfraGEN generated\n";
        }

        StringBuilder content = new StringBuilder();
        content.append("# InfraGEN generated environment variables\n");
        content.append("# 민감한 정보는 이 파일에만 저장하세요. 버전 관리에 커밋하지 마세요.\n");
        content.append('\n');

        for (Map.Entry<String, String> entry : envVars.entrySet()) {
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

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "";
    }
}
