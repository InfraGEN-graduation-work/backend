package com.infragen.infragen.domain.generation.generator.cloud;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.NodeJsComponent;
import com.infragen.infragen.global.enums.ComponentType;

/**
 * Node.js 애플리케이션의 CLOUD_DEPLOY runtime Dockerfile과 {@code .dockerignore}를 만든다.
 *
 * <p>빌드 컨텍스트는 생성 파일이 놓이는 {@code cloud/} 디렉터리이고, 사용자가 앱 파일을 그 안에 둔다. 운영 의존성만
 * 설치하고 실행은 {@code package.json}의 {@code start} 스크립트에 맡긴다. 앱이 {@code /app} 아래에 파일을 쓸 수 있도록
 * 디렉터리와 복사 파일, 설치 결과의 소유자를 {@code node}로 맞춘다.
 *
 * <p>{@code .dockerignore}가 없으면 {@code COPY . .}가 {@code .env}의 비밀번호를 이미지에 넣으므로 함께 만든다.
 */
@Component
public class NodeJsRuntimeDockerfileRenderer implements RuntimeDockerfileRenderer {

    private static final Pattern NODE_VERSION = Pattern.compile("\\d+");

    private static final String DOCKERIGNORE = """
        .env
        .env.*
        .git
        node_modules
        aws
        oci
        Dockerfile
        .dockerignore
        docker-compose.cloud.yml
        CLOUD_DEPLOY_WARNING.md
        """;

    @Override
    public ComponentType getApplicationType() {
        return ComponentType.NODE_JS;
    }

    @Override
    public String requiredAppFiles() {
        return "`package.json`, `package-lock.json`, 앱 파일";
    }

    @Override
    public IaCFileDTO.FileContentResDTO render(CloudDeployContext context) {
        NodeJsComponent application = nodeJsApplication(context);
        String nodeVersion = application.getNodeVersion();
        // parser가 형식을 검증하지만 값이 FROM 줄에 그대로 들어가므로 여기서도 내부 계약 위반으로 거부한다.
        if (nodeVersion == null || !NODE_VERSION.matcher(nodeVersion).matches()) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }

        String content = """
            # InfraGEN runtime 이미지입니다. 빌드 컨텍스트에 package.json, package-lock.json과 앱 파일이 필요합니다.
            # 종료 신호를 앱이 받으려면 package.json의 start 스크립트가 exec로 앱을 실행해야 합니다. 예: "exec node server.js"
            FROM node:%s-slim

            WORKDIR /app
            RUN chown node:node /app
            ENV NODE_ENV=production
            USER node

            COPY --chown=node:node package.json package-lock.json ./
            RUN npm ci --omit=dev

            COPY --chown=node:node . .
            EXPOSE %d
            CMD ["npm", "start"]
            """.formatted(nodeVersion, application.getPort());

        return IaCFileDTO.FileContentResDTO.builder()
            .fileName("Dockerfile")
            .content(content)
            .build();
    }

    @Override
    public List<IaCFileDTO.FileContentResDTO> renderFiles(CloudDeployContext context) {
        return List.of(render(context), dockerignore());
    }

    private IaCFileDTO.FileContentResDTO dockerignore() {
        return IaCFileDTO.FileContentResDTO.builder()
            .fileName(".dockerignore")
            .content(DOCKERIGNORE)
            .build();
    }

    private NodeJsComponent nodeJsApplication(CloudDeployContext context) {
        if (!(context.application() instanceof NodeJsComponent application)) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }
        return application;
    }
}
