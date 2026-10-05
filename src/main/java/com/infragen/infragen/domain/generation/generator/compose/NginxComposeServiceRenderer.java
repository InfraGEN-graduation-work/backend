package com.infragen.infragen.domain.generation.generator.compose;

import java.util.List;
import org.springframework.stereotype.Component;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.generator.nginx.NginxConfigRenderer;
import com.infragen.infragen.domain.parsing.dto.response.ApplicationComponent;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.NginxComponent;
import com.infragen.infragen.global.enums.ComponentType;

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
        ApplicationComponent app = context.findIncomingDependencies(component.getNodeId()).stream()
            .filter(ApplicationComponent.class::isInstance).map(ApplicationComponent.class::cast)
            .findFirst().orElseThrow();
        return List.of(NginxConfigRenderer.render("host.docker.internal", app.getPort()));
    }
}
