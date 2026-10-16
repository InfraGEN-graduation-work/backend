package com.infragen.infragen.domain.generation.generator.cloud;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.parsing.dto.response.CaddyComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;
import com.infragen.infragen.global.enums.ComponentType;

@DisplayName("CLOUD_DEPLOY 실행 정보의 타입별 component 조회")
class CloudDeployContextTest {

    @Test
    @DisplayName("edge가 없어도 지정한 타입의 component를 찾는다")
    void component_ExistingType_ReturnsComponentRegardlessOfEdges() {
        // given
        CloudDeployContext context = context();

        // when
        Optional<CaddyComponent> caddy = context.component(ComponentType.CADDY, CaddyComponent.class);

        // then
        assertAll(
            () -> assertTrue(caddy.isPresent()),
            () -> assertEquals("caddy-1", caddy.orElseThrow().getNodeId())
        );
    }

    @Test
    @DisplayName("그래프에 없는 타입이면 빈 값을 돌려준다")
    void component_MissingType_ReturnsEmpty() {
        // given
        CloudDeployContext context = context();

        // when
        Optional<CaddyComponent> nginx = context.component(ComponentType.NGINX, CaddyComponent.class);

        // then
        assertTrue(nginx.isEmpty());
    }

    private static CloudDeployContext context() {
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        parsingResult.setProjectId(1L);
        parsingResult.setComponents(List.of(
            SpringBootComponent.builder()
                .id("node-1")
                .name("app")
                .port(9090)
                .javaVersion("17")
                .containerName("app")
                .build(),
            CaddyComponent.builder()
                .id("caddy-1")
                .imageVersion("caddy:2.8")
                .containerName("caddy")
                .domain("example.com")
                .volumeName("caddy_data")
                .build()
        ));
        return CloudDeployContext.from(parsingResult);
    }
}
