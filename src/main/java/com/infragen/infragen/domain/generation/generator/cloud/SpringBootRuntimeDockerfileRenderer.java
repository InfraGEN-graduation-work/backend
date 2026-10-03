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
}
