package com.infragen.infragen.domain.generation.generator.cloud;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.response.NginxComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;

@DisplayName("CLOUD_DEPLOY NGINX Compose service renderer")
class NginxCloudComposeServiceRendererTest {

    private final NginxCloudComposeServiceRenderer renderer = new NginxCloudComposeServiceRenderer();

    @Test
    @DisplayName("안내 섹션은 NGINX 포트, 프록시 대상, Terraform app_port, 설정 파일 변경 방법을 안내한다")
    void guideSection_WithNginx_GuidesPortsAndConfigReload() {
        // given
        CloudDeployContext context = context(9090, 8080);

        // when
        String section = renderer.guideSection(context).orElseThrow();

        // then
        assertAll(
            () -> assertTrue(section.startsWith("## NGINX 웹 서버\n")),
            () -> assertTrue(section.contains("호스트 포트(`8080`)로 요청을 받아 `app:9090` 주소로 전달")),
            () -> assertTrue(section.contains("`nginx/default.conf`에 있고")),
            () -> assertTrue(section.contains("`app_port` 변수에는 앱 포트가 아니라 NGINX 포트(`8080`)가 들어갑니다")),
            () -> assertTrue(section.contains("`aws_app_cidr` 또는 `oci_app_cidr`")),
            () -> assertTrue(section.contains("docker compose -f docker-compose.cloud.yml restart nginx")),
            () -> assertTrue(section.contains("앱 포트(`9090`)는 호스트에 공개하지 않습니다"))
        );
    }

    private static CloudDeployContext context(int applicationPort, int nginxPort) {
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        parsingResult.setProjectId(1L);
        parsingResult.setComponents(List.of(
            SpringBootComponent.builder()
                .id("node-1")
                .name("app")
                .port(applicationPort)
                .javaVersion("17")
                .containerName("app")
                .build(),
            NginxComponent.builder()
                .id("nginx-1")
                .imageVersion("nginx:1.27")
                .containerName("nginx")
                .port(nginxPort)
                .build()));
        // NGINX는 앱에서 NGINX로 향하는 연결이 있어야 CLOUD 산출물에 포함된다.
        EdgeDTO edge = new EdgeDTO();
        edge.setSourceNodeId("node-1");
        edge.setTargetNodeId("nginx-1");
        parsingResult.setEdges(List.of(edge));
        return CloudDeployContext.from(parsingResult);
    }
}
