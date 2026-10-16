package com.infragen.infragen.domain.parsing.dto.response;

import com.infragen.infragen.global.enums.ComponentType;

import lombok.Builder;
import lombok.Getter;

/**
 * CADDY 웹 서버 노드의 파싱 결과다.
 *
 * <p>호스트 포트는 받지 않는다. 80/443으로 고정해 포트 검증 대상에서 빠진다.
 * {@code domain}이 비어 있으면 HTTP만 서비스하고, 있으면 Caddy가 HTTPS 인증서를 자동 발급한다.
 *
 * <p>{@code domain}과 {@code volumeName}은 만들 때 앞뒤 공백을 제거하고 null을 빈 문자열로 맞춘다. 사용하는 쪽이
 * 같은 정리를 반복하지 않게 하기 위해서다.
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
        this.domain = domain == null ? "" : domain.trim();
        this.volumeName = volumeName == null ? "" : volumeName.trim();
    }

    /** @return 도메인이 지정돼 Caddy가 HTTPS를 쓰는지 여부 */
    public boolean hasDomain() {
        return !domain.isEmpty();
    }
}
