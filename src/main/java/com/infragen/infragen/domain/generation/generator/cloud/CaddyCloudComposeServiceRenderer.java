package com.infragen.infragen.domain.generation.generator.cloud;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.parsing.dto.response.CaddyComponent;
import com.infragen.infragen.global.enums.ComponentType;

/**
 * 그래프의 Caddy를 CLOUD_DEPLOY Compose service로 렌더링한다.
 *
 * <p>Caddy는 앱의 의존 인프라가 아니라 앱 앞단 웹 서버라 앱의 {@code depends_on}과 환경변수에 영향을 주지 않고,
 * 반대로 Caddy service가 {@code app}에 의존한다.
 */
@Component
public class CaddyCloudComposeServiceRenderer implements CloudComposeServiceRenderer {

    /** @return 이 renderer가 담당하는 Caddy component type */
    @Override
    public ComponentType getSupportedType() {
        return ComponentType.CADDY;
    }

    @Override
    public String getServiceName() {
        return "caddy";
    }

    /** 그래프에 Caddy가 있을 때만 Cloud Compose에 포함한다. 앱 연결 규칙은 parsing 단계에서 검증한다. */
    @Override
    public boolean isEnabled(CloudDeployContext context) {
        return caddy(context).isPresent();
    }

    /** @return 항상 false. 앱이 Caddy를 쓰는 것이 아니라 Caddy가 앱을 프록시한다. */
    @Override
    public boolean isDependency(CloudDeployContext context) {
        return false;
    }

    @Override
    public String render(CloudDeployContext context) {
        CaddyComponent caddy = caddy(context).orElseThrow();
        // Caddyfile은 파일이 아닌 디렉터리를 마운트한다. 편집기가 파일을 교체하면 컨테이너가 변경을 못 읽을 수 있다.
        StringBuilder block = new StringBuilder("""

              caddy:
                image: %s
                ports:
                  - "80:80"
                  - "443:443"
                volumes:
                  - ./caddy:/etc/caddy
            """.formatted(caddy.getImageVersion()));
        String volumeName = caddy.getVolumeName();
        if (volumeName != null && !volumeName.isBlank()) {
            block.append("      - ").append(volumeName.trim()).append(":/data\n");
        }
        block.append("    depends_on:\n      - app\n");
        return block.toString();
    }

    /** @return 앱 컨테이너에 넘길 환경변수가 없으므로 빈 값 */
    @Override
    public SequencedMap<String, String> applicationEnvironment() {
        return new LinkedHashMap<>();
    }

    private Optional<CaddyComponent> caddy(CloudDeployContext context) {
        return context.component(ComponentType.CADDY, CaddyComponent.class);
    }
}
