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
        **/.env
        **/.env.*
        **/.git
        **/node_modules
        aws
        oci
        Dockerfile
        .dockerignore
        docker-compose.cloud.yml
        CLOUD_DEPLOY_WARNING.md
        CLOUD_DEPLOY_GUIDE.md
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

    /**
     * @return 앱 파일 배치, {@code start} 스크립트의 {@code exec}, {@code PORT} listen, DB 중립 변수를 안내하는 섹션
     */
    @Override
    public String guideSection(CloudDeployContext context) {
        return """
            ## Node.js 앱

            `app` 서비스는 `Dockerfile`로 이 디렉터리의 앱 파일을 이미지에 넣고 `npm start`로 실행합니다. 이미지는 `npm ci --omit=dev`로 운영 의존성만 설치합니다.

            ### 배포 전에 준비할 것

            - `package.json`, `package-lock.json`과 앱 파일을 `Dockerfile`과 같은 디렉터리에 두어 주세요. 생성된 `.dockerignore`가 하위 폴더를 포함해 `.env`, `.env.*`, `.git`, `node_modules`를 이미지에서 제외합니다.
            - `package.json`의 `start` 스크립트는 `exec node server.js`처럼 `exec`로 앱을 실행해야 합니다. 그래야 `docker stop`의 종료 신호(SIGTERM)가 앱에 전달됩니다. 앱이 `process.on("SIGTERM", ...)`으로 신호를 받아 정리하는 코드를 가져야 정리 작업이 실행됩니다.
            - 앱은 `process.env.PORT`로 listen해야 합니다. Compose가 앱 컨테이너에 `PORT=%d`(그래프에 입력한 포트)를 넣습니다.

            ### DB 접속 설정

            - DB나 Redis를 연결하면 접속 정보가 `MYSQL_HOST`, `MYSQL_PORT`처럼 종류별 변수로 앱 컨테이너에 들어갑니다. 앱 코드에서 이 변수로 연결 설정을 직접 만들어 주세요. 변수 이름은 이 문서의 DB, Redis 섹션에 있습니다.

            ### 실행

            ```
            docker compose -f docker-compose.cloud.yml up -d --build
            ```
            """.formatted(context.applicationPort());
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
