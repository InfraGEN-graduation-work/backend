package com.infragen.infragen.domain.parsing.parser;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.CaddyComponent;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.domain.parsing.validator.ComponentPropertyField;
import com.infragen.infragen.domain.parsing.validator.ComposeYamlValueValidator;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;

/**
 * CADDY 웹 서버 노드의 속성을 검증하고 {@link CaddyComponent}로 바꾼다.
 *
 * <p>호스트 포트는 80/443으로 고정이라 포트를 받지 않고, 그래서 {@code ParsingService}의 포트 범위와 중복 검사를 거치지 않는다.
 * {@code domain}은 Caddyfile의 사이트 주소로 그대로 들어가므로 공백, 중괄호, 줄바꿈처럼 설정을 바꿀 수 있는 문자를
 * 막으려고 점을 포함한 호스트명 형식만 허용한다. 도메인이 있으면 자동 HTTPS 인증서를 보관할 볼륨이 필요하다.
 * {@code imageVersion}과 {@code volumeName}도 Compose YAML에 그대로 들어가므로 {@link ComposeYamlValueValidator}로 형식을 검증해 거부한다.
 */
@Component
public class CaddyParser implements ComponentParser {

    // 점으로 구분한 라벨의 연속이며 마지막 라벨은 영문자로 시작해야 한다. 숫자로 끝나는 IP와 점이 없는 localhost를 거부한다.
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?");

    @Override
    public ComponentType getSupportedType() {
        return ComponentType.CADDY;
    }

    @Override
    public boolean requiresPort() {
        return false;
    }

    @Override
    public BaseComponent parse(NodeDTO node, JsonNode props, int port) {
        String imageVersion = ComponentPropertyField.CADDY_IMAGE_VERSION.read(props);
        String containerName = props.path("containerName").asString();
        String domain = props.path("domain").asString();
        String volumeName = ComponentPropertyField.CADDY_VOLUME_NAME.read(props);

        if (imageVersion == null || imageVersion.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_CADDY_IMAGE_VERSION);
        }

        String resolvedImageVersion = imageVersion.trim();
        if (!ComposeYamlValueValidator.isImageReference(resolvedImageVersion)) {
            throw new ParsingException(ParsingErrorCode.INVALID_CADDY_IMAGE_VERSION);
        }

        String resolvedDomain = domain == null ? "" : domain.trim();
        String resolvedVolumeName = volumeName == null ? "" : volumeName.trim();
        if (!resolvedDomain.isEmpty()) {
            if (!DOMAIN_PATTERN.matcher(resolvedDomain).matches()) {
                throw new ParsingException(ParsingErrorCode.INVALID_CADDY_DOMAIN);
            }
            if (resolvedVolumeName.isEmpty()) {
                throw new ParsingException(ParsingErrorCode.MISSING_CADDY_VOLUME_NAME);
            }
        }
        // 볼륨은 도메인이 없어도 이름이 있으면 마운트되므로 도메인 유무와 상관없이 형식을 검사한다.
        if (!resolvedVolumeName.isEmpty() && !ComposeYamlValueValidator.isVolumeName(resolvedVolumeName)) {
            throw new ParsingException(ParsingErrorCode.INVALID_CADDY_VOLUME_NAME);
        }

        float posX = node.getPositionX() != null ? node.getPositionX() : 0f;
        float posY = node.getPositionY() != null ? node.getPositionY() : 0f;

        return CaddyComponent.builder()
            .id(node.getNodeId())
            .posX(posX)
            .posY(posY)
            .imageVersion(resolvedImageVersion)
            .containerName(containerName != null ? containerName : "")
            .domain(resolvedDomain)
            .volumeName(resolvedVolumeName)
            .build();
    }
}
