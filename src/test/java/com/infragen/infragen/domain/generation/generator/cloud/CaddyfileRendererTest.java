package com.infragen.infragen.domain.generation.generator.cloud;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

@DisplayName("CLOUD_DEPLOY Caddyfile renderer")
class CaddyfileRendererTest {

    private final CaddyfileRenderer renderer = new CaddyfileRenderer();

    @Test
    @DisplayName("도메인이 있으면 도메인을 사이트 주소로 앱 컨테이너를 프록시한다")
    void render_WithDomain_ProxiesToAppServiceUnderDomain() {
        // given
        CloudDeployContext context = context("example.com", true);

        // when
        Optional<IaCFileDTO.FileContentResDTO> file = renderer.render(context);

        // then
        assertAll(
            () -> assertTrue(file.isPresent()),
            () -> assertEquals("caddy/Caddyfile", file.orElseThrow().fileName()),
            () -> assertEquals("""
                example.com {
                    reverse_proxy app:9090
                }
                """, file.orElseThrow().content())
        );
    }

    @Test
    @DisplayName("도메인이 없으면 :80 사이트 주소로 HTTP만 프록시한다")
    void render_WithoutDomain_UsesHttpOnlyAddress() {
        // given
        CloudDeployContext context = context("", true);

        // when
        Optional<IaCFileDTO.FileContentResDTO> file = renderer.render(context);

        // then
        assertEquals("""
            :80 {
                reverse_proxy app:9090
            }
            """, file.orElseThrow().content());
    }

    @Test
    @DisplayName("Caddy가 없으면 Caddyfile을 만들지 않는다")
    void render_WithoutCaddy_ReturnsEmpty() {
        // given
        CloudDeployContext context = context("example.com", false);

        // when
        Optional<IaCFileDTO.FileContentResDTO> file = renderer.render(context);

        // then
        assertTrue(file.isEmpty());
    }

    private static CloudDeployContext context(String domain, boolean withCaddy) {
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
                .volumeName("caddy_data")
                .build());
        }
        parsingResult.setComponents(components);
        return CloudDeployContext.from(parsingResult);
    }
}
