package com.infragen.infragen.domain.generation.generator.cloud;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.generator.compose.ComposeYamlSupport;
import com.infragen.infragen.domain.parsing.dto.response.CaddyComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * 그래프의 Caddy를 CLOUD_DEPLOY Compose service로 렌더링한다.
 *
 * <p>Caddy는 앱의 의존 인프라가 아니라 앱 앞단 웹 서버라 앱의 {@code depends_on}과 환경변수에 영향을 주지 않고,
 * 반대로 Caddy service가 {@code app}에 의존한다.
 */
@Component
public class CaddyCloudComposeServiceRenderer implements CloudComposeServiceRenderer {

    // HTTP 챌린지와 자동 HTTPS가 80/443을 쓰므로 호스트 포트는 입력받지 않고 고정한다.
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;

    private final CaddyfileRenderer caddyfileRenderer = new CaddyfileRenderer();
    private final CaddyGuideRenderer caddyGuideRenderer = new CaddyGuideRenderer();

    /**
     * @return 이 renderer가 담당하는 Caddy component type
     */
    @Override
    public ComponentType getSupportedType() {
        return ComponentType.CADDY;
    }

    @Override
    public String getServiceName() {
        return "caddy";
    }

    /**
     * 그래프에 Caddy가 있을 때만 Cloud Compose에 포함한다. 앱 연결 규칙은 parsing 단계에서 검증한다.
     */
    @Override
    public boolean isEnabled(CloudDeployContext context) {
        return caddy(context).isPresent();
    }

    /**
     * @return 항상 false. 앱이 Caddy를 쓰는 것이 아니라 Caddy가 앱을 프록시한다.
     */
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
                      - "%d:%d"
                      - "%d:%d"
                    volumes:
                      - ./caddy:/etc/caddy
                """.formatted(ComposeYamlSupport.toYamlString(ComposeYamlSupport.resolveImage(caddy.getImageVersion(), null)), HTTP_PORT, HTTP_PORT, HTTPS_PORT, HTTPS_PORT));
        volumeName(context).ifPresent(volumeName -> block.append("      - ").append(volumeName).append(":/data\n"));
        block.append("    depends_on:\n      - app\n");
        return block.toString();
    }

    /**
     * @return Caddy 컨테이너가 읽는 {@code caddy/Caddyfile}과 사용자 안내 {@code CADDY_GUIDE.md}
     */
    @Override
    public List<IaCFileDTO.FileContentResDTO> additionalFiles(CloudDeployContext context) {
        return Stream.of(caddyfileRenderer.render(context), caddyGuideRenderer.render(context))
            .flatMap(Optional::stream)
            .toList();
    }

    /**
     * @return 호스트에 공개하는 HTTP·HTTPS 포트. Terraform이 이 포트의 ingress를 연다.
     */
    @Override
    public List<Integer> publicPorts(CloudDeployContext context) {
        return List.of(HTTP_PORT, HTTPS_PORT);
    }

    /**
     * @return Caddy가 유일한 외부 진입점이라 앱의 호스트 포트를 닫으므로 항상 {@code true}
     */
    @Override
    public boolean closesApplicationPort(CloudDeployContext context) {
        return true;
    }

    /**
     * @return 앱 컨테이너에 넘길 환경변수가 없으므로 빈 값
     */
    @Override
    public SequencedMap<String, String> applicationEnvironment() {
        return new LinkedHashMap<>();
    }

    private Optional<CaddyComponent> caddy(CloudDeployContext context) {
        return context.component(ComponentType.CADDY, CaddyComponent.class);
    }
}
