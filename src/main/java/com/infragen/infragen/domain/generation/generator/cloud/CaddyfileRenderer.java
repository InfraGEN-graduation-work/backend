package com.infragen.infragen.domain.generation.generator.cloud;

import java.util.Optional;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.parsing.dto.response.CaddyComponent;
import com.infragen.infragen.global.enums.ComponentType;

/** CLOUD_DEPLOY Caddy 컨테이너가 읽을 Caddyfile을 생성한다. */
public class CaddyfileRenderer {

    // 사이트 주소가 비어 있으면 도메인 없이 HTTP 80만 받는다. 이때 Caddy는 자동 HTTPS를 켜지 않는다.
    private static final String HTTP_ONLY_ADDRESS = ":80";

    /**
     * 그래프에 Caddy가 있으면 앱 컨테이너로 프록시하는 Caddyfile을 만든다.
     *
     * <p>프록시 대상은 Compose의 {@code app} service 이름과 앱 포트다. 도메인 형식 검증은 parsing 단계에서 끝났다고 보고,
     * 값을 그대로 사이트 주소에 쓴다.
     *
     * @param context CLOUD_DEPLOY renderer가 공유하는 파싱 결과
     * @return Caddy가 없으면 빈 값
     */
    public Optional<IaCFileDTO.FileContentResDTO> render(CloudDeployContext context) {
        return context.component(ComponentType.CADDY, CaddyComponent.class).map(caddy -> IaCFileDTO.FileContentResDTO.builder()
            .fileName("caddy/Caddyfile")
            .content("""
                %s {
                    reverse_proxy app:%d
                }
                """.formatted(siteAddress(caddy), context.applicationPort()))
            .build());
    }

    private String siteAddress(CaddyComponent caddy) {
        return caddy.hasDomain() ? caddy.getDomain() : HTTP_ONLY_ADDRESS;
    }
}
