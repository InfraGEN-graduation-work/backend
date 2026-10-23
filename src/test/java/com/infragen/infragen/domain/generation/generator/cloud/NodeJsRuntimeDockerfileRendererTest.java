package com.infragen.infragen.domain.generation.generator.cloud;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.ApplicationComponent;
import com.infragen.infragen.domain.parsing.dto.response.NodeJsComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;
import com.infragen.infragen.global.enums.ComponentType;

class NodeJsRuntimeDockerfileRendererTest {

    private final NodeJsRuntimeDockerfileRenderer renderer = new NodeJsRuntimeDockerfileRenderer();

    @Test
    @DisplayName("Node.js 앱 타입을 지원한다")
    void getApplicationType_returnsNodeJs() {
        // when
        ComponentType applicationType = renderer.getApplicationType();

        // then
        assertEquals(ComponentType.NODE_JS, applicationType);
    }

    @Test
    @DisplayName("경고 문서에 적을 준비물로 package.json, package-lock.json, 앱 파일을 안내한다")
    void requiredAppFiles_returnsNodeFiles() {
        // when
        String requiredAppFiles = renderer.requiredAppFiles();

        // then
        assertEquals("`package.json`, `package-lock.json`, 앱 파일", requiredAppFiles);
    }

    @Test
    @DisplayName("운영 의존성만 설치하고 npm start로 실행하는 Dockerfile을 기준 출력과 같게 만든다")
    void render_nodeJsApplication_returnsExactDockerfile() {
        // given
        CloudDeployContext context = context(nodeJsComponent("22", 3000));

        // when
        IaCFileDTO.FileContentResDTO dockerfile = renderer.render(context);

        // then
        assertAll(
            () -> assertEquals("Dockerfile", dockerfile.fileName()),
            () -> assertEquals("""
                # InfraGEN runtime 이미지입니다. 빌드 컨텍스트에 package.json, package-lock.json과 앱 파일이 필요합니다.
                # 종료 신호를 앱이 받으려면 package.json의 start 스크립트가 exec로 앱을 실행해야 합니다. 예: "exec node server.js"
                FROM node:22-slim

                WORKDIR /app
                RUN chown node:node /app
                ENV NODE_ENV=production
                USER node

                COPY --chown=node:node package.json package-lock.json ./
                RUN npm ci --omit=dev

                COPY --chown=node:node . .
                EXPOSE 3000
                CMD ["npm", "start"]
                """, dockerfile.content())
        );
    }

    @Test
    @DisplayName("renderFiles는 Dockerfile 다음에 .dockerignore를 두고 하위 폴더의 .env와 node_modules까지 제외한다")
    void renderFiles_nodeJsApplication_returnsDockerfileAndDockerignore() {
        // given
        CloudDeployContext context = context(nodeJsComponent("22", 3000));

        // when
        List<IaCFileDTO.FileContentResDTO> files = renderer.renderFiles(context);

        // then
        List<String> dockerignoreLines = files.get(1).content().lines().toList();
        assertAll(
            () -> assertEquals(2, files.size()),
            () -> assertEquals("Dockerfile", files.get(0).fileName()),
            () -> assertEquals(".dockerignore", files.get(1).fileName()),
            () -> assertTrue(dockerignoreLines.contains("**/.env")),
            () -> assertTrue(dockerignoreLines.contains("**/.env.*")),
            () -> assertTrue(dockerignoreLines.contains("**/.git")),
            () -> assertTrue(dockerignoreLines.contains("**/node_modules")),
            () -> assertTrue(dockerignoreLines.containsAll(List.of(
                "aws", "oci", "Dockerfile", ".dockerignore", "docker-compose.cloud.yml", "CLOUD_DEPLOY_WARNING.md",
                "CLOUD_DEPLOY_GUIDE.md")))
        );
    }

    @Test
    @DisplayName("앱 섹션은 앱 파일 배치, start 스크립트의 exec, 그래프 포트의 PORT listen을 안내한다")
    void guideSection_nodeJs_guidesAppFilesExecAndPort() {
        // given
        CloudDeployContext context = context(nodeJsComponent("22", 3000));

        // when
        String section = renderer.guideSection(context);

        // then
        assertAll(
            () -> assertTrue(section.startsWith("## Node.js 앱\n")),
            () -> assertTrue(section.contains("`package.json`, `package-lock.json`과 앱 파일을 `Dockerfile`과 같은 디렉터리에")),
            () -> assertTrue(section.contains("`exec node server.js`처럼 `exec`로")),
            () -> assertTrue(section.contains("`process.on(\"SIGTERM\", ...)`으로 신호를 받아 정리하는 코드")),
            () -> assertTrue(section.contains("`PORT=3000`(그래프에 입력한 포트)를 넣습니다")),
            () -> assertTrue(section.contains("docker compose -f docker-compose.cloud.yml up -d --build"))
        );
    }

    @Test
    @DisplayName("nodeVersion이 숫자가 아니면 Dockerfile을 만들지 않고 INVALID_COMPONENT_STATE로 거부")
    void render_nonNumericNodeVersion_throwsInvalidComponentState() {
        // given
        CloudDeployContext context = context(nodeJsComponent("22 && rm -rf /", 3000));

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> renderer.render(context)
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
    }

    @Test
    @DisplayName("Spring Boot 컨텍스트를 넘기면 INVALID_COMPONENT_STATE로 거부")
    void render_springBootContext_throwsInvalidComponentState() {
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
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> renderer.render(context)
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
    }

    private static NodeJsComponent nodeJsComponent(String nodeVersion, int port) {
        return NodeJsComponent.builder()
            .id("node-1")
            .posX(0f)
            .posY(0f)
            .name("api")
            .port(port)
            .nodeVersion(nodeVersion)
            .containerName("node-app")
            .build();
    }

    private static CloudDeployContext context(ApplicationComponent application) {
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        parsingResult.setProjectId(1L);
        parsingResult.setComponents(List.of(application));
        return CloudDeployContext.from(parsingResult);
    }
}
