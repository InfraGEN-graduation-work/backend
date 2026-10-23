package com.infragen.infragen.domain.parsing.validator;

import java.util.regex.Pattern;

/**
 * Compose YAML에 그대로 들어가는 이미지 참조와 볼륨 이름이 Docker 형식인지 판정한다.
 *
 * <p>renderer는 이 값을 인코딩 없이 {@code image:} 줄, 볼륨 마운트 줄, 루트 {@code volumes:} 키에 쓰므로 줄바꿈, 공백,
 * {@code ${...}} 변수 참조가 YAML 구조를 바꾸지 못하게 파싱 단계에서 거부한다. 오류 코드는 노드 타입별로 다르므로 판정만 하고 예외는 호출하는 파서가 던진다. 파서마다 정규식이 어긋나지 않도록
 * 한곳에 둔다.
 */
public final class ComposeYamlValueValidator {

    // Docker 이미지 참조 문법(이름[:태그][@다이제스트])이다. 값이 빈 태그(caddy:)나 짧은 다이제스트처럼 Docker가 거부할 구조도 걸러낸다.
    // 도메인 라벨, 저장소 경로(소문자), 태그, 다이제스트의 정의는 distribution/reference의 regexp.go를 따르고, IPv6 레지스트리는 허용하지 않는다.
    private static final String DOMAIN_COMPONENT = "(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])";
    private static final String REGISTRY = DOMAIN_COMPONENT + "(?:\\." + DOMAIN_COMPONENT + ")*(?::[0-9]+)?";
    private static final String PATH_COMPONENT = "[a-z0-9]+(?:(?:[._]|__|-+)[a-z0-9]+)*";
    private static final String NAME = "(?:" + REGISTRY + "/)?" + PATH_COMPONENT + "(?:/" + PATH_COMPONENT + ")*";
    private static final String TAG = "[\\w][\\w.-]{0,127}";
    // regexp.go의 다이제스트 문법(알고리즘:16진수 32자 이상)은 1차 판정이고, Docker는 이어서 go-digest로 알고리즘별 정확한 길이와
    // 소문자 16진수를 다시 검사한다. 문법만 따르면 sha256:에 32자나 대문자를 붙인 값이 파싱을 통과하고 pull에서 거부되므로 go-digest 기준을 쓴다.
    private static final String DIGEST = "(?:sha256:[a-f0-9]{64}|sha384:[a-f0-9]{96}|sha512:[a-f0-9]{128})";
    private static final Pattern IMAGE_REFERENCE_PATTERN =
            Pattern.compile(NAME + "(?::" + TAG + ")?(?:@" + DIGEST + ")?");

    // Docker 볼륨 이름 규칙이다. 마운트 줄과 루트 volumes 키에 들어가므로 콜론, 공백, 줄바꿈이 구조를 바꾸지 못하게 한다.
    private static final Pattern VOLUME_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_.-]*");

    private ComposeYamlValueValidator() {
    }

    /**
     * 값이 Docker 이미지 참조 형식이면 true를 돌려준다.
     *
     * <p>trim하지 않고 판정하므로 호출자는 앞뒤 공백을 제거한 값을 넘겨야 한다. null과 빈 값은 false다.
     */
    public static boolean isImageReference(String value) {
        return value != null && IMAGE_REFERENCE_PATTERN.matcher(value).matches();
    }

    /**
     * 값이 Docker 볼륨 이름 규칙을 따르면 true를 돌려준다.
     *
     * <p>trim하지 않고 판정한다. 빈 값은 false이므로 볼륨이 선택 항목인 노드는 빈 값을 호출 전에 걸러야 한다.
     */
    public static boolean isVolumeName(String value) {
        return value != null && VOLUME_NAME_PATTERN.matcher(value).matches();
    }
}
