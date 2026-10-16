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

import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.CaddyComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;
import com.infragen.infragen.global.enums.ComponentType;

@DisplayName("CLOUD_DEPLOY Caddy Compose service renderer")
class CaddyCloudComposeServiceRendererTest {

    private final CaddyCloudComposeServiceRenderer renderer = new CaddyCloudComposeServiceRenderer();

    @Test
    @DisplayName("볼륨이 있으면 Caddyfile 디렉터리와 /data 볼륨을 마운트하고 app에 의존한다")
    void render_WithVolume_MountsCaddyDirectoryAndDataVolume() {
        // given
        CloudDeployContext context = context("caddy_data", true);

        // when
        String block = renderer.render(context);

        // then
        assertEquals("""

              caddy:
                image: caddy:2.8
                ports:
                  - "80:80"
                  - "443:443"
                volumes:
                  - ./caddy:/etc/caddy
                  - caddy_data:/data
                depends_on:
                  - app
            """, block);
    }

    @Test
    @DisplayName("볼륨이 없으면 /data 마운트를 만들지 않는다")
    void render_WithoutVolume_OmitsDataMount() {
        // given
        CloudDeployContext context = context("", true);

        // when
        String block = renderer.render(context);

        // then
        assertAll(
            () -> assertTrue(block.contains("      - ./caddy:/etc/caddy\n")),
            () -> assertFalse(block.contains(":/data"))
        );
    }

    @Test
    @DisplayName("볼륨 이름이 있으면 루트에 선언할 이름으로 돌려준다")
    void volumeName_WithVolume_ReturnsName() {
        // given
        CloudDeployContext context = context(" caddy_data ", true);

        // when
        Optional<String> volumeName = renderer.volumeName(context);

        // then
        assertEquals(Optional.of("caddy_data"), volumeName);
    }

    @Test
    @DisplayName("볼륨 이름이 비어 있으면 빈 값을 돌려준다")
    void volumeName_BlankVolume_ReturnsEmpty() {
        // given
        CloudDeployContext context = context("", true);

        // when
        Optional<String> volumeName = renderer.volumeName(context);

        // then
        assertTrue(volumeName.isEmpty());
    }

    @Test
    @DisplayName("Caddy가 있을 때만 활성화하고 앱 의존이나 앱 환경변수에는 관여하지 않는다")
    void isEnabled_OnlyWhenCaddyExists_AndNeverAppDependency() {
        // given
        CloudDeployContext withCaddy = context("caddy_data", true);
        CloudDeployContext withoutCaddy = context("caddy_data", false);

        // when
        boolean enabledWithCaddy = renderer.isEnabled(withCaddy);
        boolean enabledWithoutCaddy = renderer.isEnabled(withoutCaddy);

        // then
        assertAll(
            () -> assertEquals(ComponentType.CADDY, renderer.getSupportedType()),
            () -> assertEquals("caddy", renderer.getServiceName()),
            () -> assertTrue(enabledWithCaddy),
            () -> assertFalse(enabledWithoutCaddy),
            () -> assertFalse(renderer.isDependency(withCaddy)),
            () -> assertTrue(renderer.applicationEnvironment().isEmpty()),
            () -> assertTrue(renderer.databaseConnection().isEmpty())
        );
    }

    private static CloudDeployContext context(String volumeName, boolean withCaddy) {
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
                .domain("example.com")
                .volumeName(volumeName)
                .build());
        }
        parsingResult.setComponents(components);
        return CloudDeployContext.from(parsingResult);
    }
}
