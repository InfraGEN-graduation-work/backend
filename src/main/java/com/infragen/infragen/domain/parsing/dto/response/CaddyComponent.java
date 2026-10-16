package com.infragen.infragen.domain.parsing.dto.response;

import com.infragen.infragen.global.enums.ComponentType;

import lombok.Builder;
import lombok.Getter;

/**
 * CADDY 웹 서버 노드의 파싱 결과다.
 *
 * <p>호스트 포트는 받지 않는다. 80/443으로 고정해 포트 검증 대상에서 빠진다.
 * {@code domain}이 비어 있으면 HTTP만 서비스하고, 있으면 Caddy가 HTTPS 인증서를 자동 발급한다.
 */
@Getter
public class CaddyComponent extends BaseComponent implements VolumeComponent {
    private String imageVersion;
    private String containerName;
    private String domain;
    private String volumeName;

    @Builder
    public CaddyComponent(
        String id,
        float posX,
        float posY,
        String imageVersion,
        String containerName,
        String domain,
        String volumeName
    ) {
        super(id, posX, posY, ComponentType.CADDY);
        this.imageVersion = imageVersion;
        this.containerName = containerName;
        this.domain = domain;
        this.volumeName = volumeName;
    }
}
