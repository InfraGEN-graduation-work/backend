package com.infragen.infragen.domain.generation.generator.cloud;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;

@DisplayName("CLOUD_DEPLOY Spring Boot runtime renderer")
class SpringBootRuntimeDockerfileRendererTest {

    private final SpringBootRuntimeDockerfileRenderer renderer = new SpringBootRuntimeDockerfileRenderer();

    @Test
    @DisplayName("앱 섹션은 app.jar 배치, 그래프 포트 listen 방법, 프레임워크 변수로 전달되는 DB 접속 정보를 안내한다")
    void guideSection_SpringBoot_GuidesJarPortAndDatasource() {
        // given
        CloudDeployContext context = context(9090);

        // when
        String section = renderer.guideSection(context);

        // then
        assertAll(
            () -> assertTrue(section.startsWith("## Spring Boot 앱\n")),
            () -> assertTrue(section.contains("`app.jar`라는 이름으로 `Dockerfile`과 같은 디렉터리에")),
            () -> assertTrue(section.contains("그래프에 입력한 포트(`9090`)로 listen")),
            () -> assertTrue(section.contains("`SERVER_PORT=9090`")),
            () -> assertTrue(section.contains("`SPRING_DATASOURCE_URL`")),
            () -> assertTrue(section.contains("docker compose -f docker-compose.cloud.yml up -d --build"))
        );
    }

    private static CloudDeployContext context(int port) {
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        parsingResult.setProjectId(1L);
        parsingResult.setComponents(List.of(SpringBootComponent.builder()
            .id("node-1")
            .name("app")
            .port(port)
            .javaVersion("17")
            .containerName("app")
            .build()));
        return CloudDeployContext.from(parsingResult);
    }
}
