package com.infragen.infragen.domain.generation.generator.cloud;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.parsing.dto.response.ApplicationComponent;
import com.infragen.infragen.domain.parsing.dto.response.NodeJsComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;

class CloudDeployWarningRendererTest {

    private final CloudDeployWarningRenderer renderer = new CloudDeployWarningRenderer();

    @Test
    @DisplayName("Spring Boot 앱은 app.jar를 준비물로 안내한다")
    void render_springBootApplication_mentionsAppJar() {
        // given
        CloudDeployContext context = context(SpringBootComponent.builder()
            .id("node-1")
            .posX(0f)
            .posY(0f)
            .name("app")
            .port(8080)
            .javaVersion("17")
            .containerName("app")
            .build());

        // when
        IaCFileDTO.FileContentResDTO warning = renderer.render(context);

        // then
        assertAll(
            () -> assertEquals("CLOUD_DEPLOY_WARNING.md", warning.fileName()),
            () -> assertTrue(warning.content().contains("외부 `.env`, `app.jar`, provider 자격증명을")),
            () -> assertFalse(warning.content().contains("package.json"))
        );
    }

    @Test
    @DisplayName("Node.js 앱은 package.json, package-lock.json, 앱 파일을 준비물로 안내하고 app.jar는 말하지 않는다")
    void render_nodeJsApplication_mentionsNodeFilesInsteadOfAppJar() {
        // given
        CloudDeployContext context = context(NodeJsComponent.builder()
            .id("node-1")
            .posX(0f)
            .posY(0f)
            .name("api")
            .port(3000)
            .nodeVersion("22")
            .containerName("node-app")
            .build());

        // when
        IaCFileDTO.FileContentResDTO warning = renderer.render(context);

        // then
        assertAll(
            () -> assertTrue(warning.content().contains(
                "외부 `.env`, `package.json`, `package-lock.json`, 앱 파일, provider 자격증명을")),
            () -> assertFalse(warning.content().contains("app.jar"))
        );
    }

    private static CloudDeployContext context(ApplicationComponent application) {
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        parsingResult.setProjectId(1L);
        parsingResult.setComponents(List.of(application));
        return CloudDeployContext.from(parsingResult);
    }
}
