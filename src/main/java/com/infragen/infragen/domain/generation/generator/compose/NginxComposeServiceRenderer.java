package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.generation.generator.nginx.NginxConfigRenderer;
import com.infragen.infragen.domain.parsing.dto.response.ApplicationComponent;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.NginxComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class NginxComposeServiceRenderer implements ComposeServiceRenderer {
    @Override
    public ComponentType getSupportedType() {
        return ComponentType.NGINX;
    }

    @Override
    public String render(BaseComponent component, ComposeGenerationContext context) {
        NginxComponent nginx = (NginxComponent) component;
        return """
              nginx:
                image: "%s"
                container_name: "%s"
                ports:
                  - "%d:80"
                extra_hosts:
                  - "host.docker.internal:host-gateway"
                volumes:
                  - ./nginx/default.conf:/etc/nginx/conf.d/default.conf:ro
            """.formatted(nginx.getImageVersion(), nginx.getContainerName(), nginx.getPort());
    }

    @Override
    public List<IaCFileDTO.FileContentResDTO> additionalFiles(
        BaseComponent component, ComposeGenerationContext context) {
        return List.of(NginxConfigRenderer.render("host.docker.internal", proxiedApplication(component, context).getPort()));
    }

    /**
     * @return 호스트 포트, 호스트에서 먼저 실행할 앱 포트, {@code host.docker.internal}, 설정 파일 변경 방법을 안내하는 섹션
     */
    @Override
    public Optional<String> guideSection(BaseComponent component, ComposeGenerationContext context) {
        NginxComponent nginx = (NginxComponent) component;
        int applicationPort = proxiedApplication(component, context).getPort();
        return Optional.of("""
            ## NGINX 웹 서버

            `nginx` 컨테이너는 호스트 포트(`%d`)로 요청을 받아 호스트에서 실행 중인 앱(`host.docker.internal:%d`)으로 전달합니다. 설정은 `%s`에 있습니다.

            - 앱을 호스트에서 포트(`%d`)로 실행해 두어야 NGINX가 요청을 전달할 수 있습니다.
            - `host.docker.internal`은 Compose의 `extra_hosts`에 지정한 `host-gateway`로 호스트의 내부 IP를 가리킵니다.
            - `%s` 파일을 수정한 뒤 이 디렉터리에서 `docker compose restart nginx`를 실행해 설정을 다시 읽게 합니다.
            """.formatted(nginx.getPort(), applicationPort, NginxConfigRenderer.FILE_NAME, applicationPort,
            NginxConfigRenderer.FILE_NAME));
    }

    // 파서가 앱에서 NGINX로 향하는 연결을 검증하므로 연결된 앱이 없으면 내부 계약 위반으로 거부한다.
    private ApplicationComponent proxiedApplication(BaseComponent component, ComposeGenerationContext context) {
        return context.findIncomingDependencies(component.getNodeId()).stream()
            .filter(ApplicationComponent.class::isInstance).map(ApplicationComponent.class::cast)
            .findFirst().orElseThrow(() -> new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE));
    }
}
