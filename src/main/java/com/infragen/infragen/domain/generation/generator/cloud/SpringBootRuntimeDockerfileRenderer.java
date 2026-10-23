package com.infragen.infragen.domain.generation.generator.cloud;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;
import com.infragen.infragen.global.enums.ComponentType;

/** 파싱된 Spring Boot runtime 계약을 prebuilt JAR용 Dockerfile로 변환한다. */
@Component
public class SpringBootRuntimeDockerfileRenderer implements RuntimeDockerfileRenderer {

    @Override
    public ComponentType getApplicationType() {
        return ComponentType.SPRING_BOOT;
    }

    @Override
    public String requiredAppFiles() {
        return "`app.jar`";
    }

    /** 컴파일이 끝난 {@code app.jar}만 복사하는 Java 실행 이미지를 생성한다. */
    @Override
    public IaCFileDTO.FileContentResDTO render(CloudDeployContext context) {
        // 선택 맵을 거치지 않고 직접 호출돼도 ClassCastException 대신 내부 계약 위반으로 거부한다.
        if (!(context.application() instanceof SpringBootComponent application)) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }
        String content = """
            # InfraGEN runtime-only 이미지입니다. 빌드 컨텍스트에 prebuilt app.jar가 필요합니다.
            FROM eclipse-temurin:%s-jre

            WORKDIR /app
            COPY app.jar app.jar
            EXPOSE %d

            ENTRYPOINT ["java", "-jar", "/app/app.jar"]
            """.formatted(application.getJavaVersion(), application.getPort());

        return IaCFileDTO.FileContentResDTO.builder()
            .fileName("Dockerfile")
            .content(content)
            .build();
    }

    /**
     * @return prebuilt {@code app.jar} 배치, listen 포트, 프레임워크 변수로 전달되는 DB 접속 정보를 안내하는 섹션
     */
    @Override
    public String guideSection(CloudDeployContext context) {
        // Spring 매퍼는 포트 변수를 넘기지 않으므로 앱이 그래프 포트로 listen하도록 직접 맞춰야 Compose 포트 매핑이 맞는다.
        int port = context.applicationPort();
        return """
            ## Spring Boot 앱

            `app` 서비스는 `Dockerfile`로 만든 이미지에서 `app.jar`를 실행합니다. 이미지는 컴파일이 끝난 JAR만 복사하고 빌드하지 않습니다.

            ### 배포 전에 준비할 것

            - 앱을 빌드한 실행 가능한 JAR를 `app.jar`라는 이름으로 `Dockerfile`과 같은 디렉터리에 두어 주세요.
            - 앱이 그래프에 입력한 포트(`%d`)로 listen해야 Compose의 포트 연결이 맞습니다. Spring Boot의 기본 포트는 `8080`이므로, 다르면 `server.port`를 같은 값으로 설정하거나 외부 `.env`에 `SERVER_PORT=%d` 한 줄을 적어 주세요. 앱 컨테이너도 외부 `.env`를 읽습니다.

            ### DB 접속 설정

            - 관계형 DB(MySQL, PostgreSQL)가 하나만 연결되면 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`가 앱 컨테이너에 들어가므로 접속 설정 코드를 바꿀 필요가 없습니다.
            - 관계형 DB가 둘 이상이면 `SPRING_DATASOURCE_*`를 만들지 않습니다. `MYSQL_HOST`, `POSTGRES_HOST` 같은 DB별 변수로 DataSource를 직접 설정해 주세요.
            - MongoDB가 연결되면 `SPRING_MONGODB_*` 변수가 들어갑니다.

            ### 실행

            ```
            docker compose -f docker-compose.cloud.yml up -d --build
            ```
            """.formatted(port, port);
    }
}
