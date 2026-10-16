package com.infragen.infragen.domain.generation.generator.cloud;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.CaddyComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;

@DisplayName("CLOUD_DEPLOY Caddy 사용자 안내 renderer")
class CaddyGuideRendererTest {

    private final CaddyGuideRenderer renderer = new CaddyGuideRenderer();

    @Test
    @DisplayName("도메인이 있으면 도메인, DNS, 80/443 CIDR, 인증서 볼륨을 안내한다")
    void render_WithDomain_GuidesDnsPortsAndVolume() {
        // given
        CloudDeployContext context = context("example.com", "caddy_data", true);

        // when
        Optional<IaCFileDTO.FileContentResDTO> file = renderer.render(context);

        // then
        String content = file.orElseThrow().content();
        assertAll(
            () -> assertEquals("CADDY_GUIDE.md", file.orElseThrow().fileName()),
            () -> assertTrue(content.contains("`app:9090`로 전달하는 리버스 프록시")),
            () -> assertTrue(content.contains("도메인 `example.com`의 A 레코드")),
            () -> assertTrue(content.contains("`aws_app_cidr` 또는 `oci_app_cidr`")),
            () -> assertTrue(content.contains("볼륨 `caddy_data`의 `/data`")),
            () -> assertTrue(content.contains(
                "docker compose -f docker-compose.cloud.yml exec -w /etc/caddy caddy caddy reload")),
            () -> assertTrue(content.contains("앱 포트 9090도 호스트에 매핑")),
            () -> assertFalse(content.contains("도메인을 지정하지 않아서"))
        );
    }

    @Test
    @DisplayName("도메인이 없으면 HTTP만 동작함과 도메인 추가 방법을 안내하고 DNS 안내는 뺀다")
    void render_WithoutDomain_GuidesHttpOnly() {
        // given
        CloudDeployContext context = context("", "", true);

        // when
        Optional<IaCFileDTO.FileContentResDTO> file = renderer.render(context);

        // then
        String content = file.orElseThrow().content();
        assertAll(
            () -> assertTrue(content.contains("도메인을 지정하지 않아서 HTTP(80)만 동작")),
            () -> assertTrue(content.contains("도메인과 인증서를 보관할 볼륨 이름을 입력한 뒤 다시 생성")),
            () -> assertFalse(content.contains("A 레코드")),
            () -> assertFalse(content.contains("/data"))
        );
    }

    @Test
    @DisplayName("Caddy가 없으면 안내 파일을 만들지 않는다")
    void render_WithoutCaddy_ReturnsEmpty() {
        // given
        CloudDeployContext context = context("example.com", "caddy_data", false);

        // when
        Optional<IaCFileDTO.FileContentResDTO> file = renderer.render(context);

        // then
        assertTrue(file.isEmpty());
    }

    private static CloudDeployContext context(String domain, String volumeName, boolean withCaddy) {
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        parsingResult.setProjectId(1L);
        List<BaseComponent> components = new ArrayList<>();
        components.add(SpringBootComponent.builder()
            .id("node-1")
            .name("app")
            .port(9090)
            .javaVersion("17")
            .containerName("app")
            .build());
        if (withCaddy) {
            components.add(CaddyComponent.builder()
                .id("caddy-1")
                .imageVersion("caddy:2.8")
                .containerName("caddy")
                .domain(domain)
                .volumeName(volumeName)
                .build());
        }
        parsingResult.setComponents(components);
        return CloudDeployContext.from(parsingResult);
    }
}
