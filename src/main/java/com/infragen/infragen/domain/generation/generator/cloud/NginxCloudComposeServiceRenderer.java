package com.infragen.infragen.domain.generation.generator.cloud;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import org.springframework.stereotype.Component;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.generator.nginx.NginxConfigRenderer;
import com.infragen.infragen.domain.parsing.dto.response.NginxComponent;
import com.infragen.infragen.global.enums.ComponentType;

@Component
public class NginxCloudComposeServiceRenderer implements CloudComposeServiceRenderer {
    @Override
    public ComponentType getSupportedType() {
        return ComponentType.NGINX;
    }

    @Override
    public String getServiceName() {
        return "nginx";
    }

    @Override
    public boolean isEnabled(CloudDeployContext context) {
        return context.nginx().isPresent();
    }

    @Override
    public boolean isDependency(CloudDeployContext context) {
        return false;
    }

    @Override
    public SequencedMap<String, String> applicationEnvironment() {
        return new LinkedHashMap<>();
    }

    @Override
    public String render(CloudDeployContext context) {
        NginxComponent nginx = context.nginx().orElseThrow();
        return """

              nginx:
                image: "%s"
                container_name: "%s"
                ports:
                  - "%d:80"
                depends_on:
                  - app
                volumes:
                  - ./nginx/default.conf:/etc/nginx/conf.d/default.conf:ro
            """.formatted(nginx.getImageVersion(), nginx.getContainerName(), nginx.getPort());
    }

    @Override
    public List<IaCFileDTO.FileContentResDTO> additionalFiles(CloudDeployContext context) {
        return List.of(NginxConfigRenderer.render("app", context.applicationPort()));
    }
}
