package com.infragen.infragen.domain.generation.generator.cloud;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;

/** 파싱된 runtime 계약을 prebuilt JAR용 Dockerfile로 변환한다. */
public class RuntimeDockerfileRenderer {

    /**
     * 컴파일이 끝난 app.jar만 복사하는 실행 이미지를 생성한다.
     *
     * @param javaVersion parsing 결과의 Java major version
     * @param applicationPort parsing 결과의 애플리케이션 포트
     * @return runtime-only Dockerfile
     */
    public IaCFileDTO.FileContentResDTO render(String javaVersion, int applicationPort) {
        String content = """
            # InfraGEN runtime-only 이미지입니다. 빌드 컨텍스트에 prebuilt app.jar가 필요합니다.
            FROM eclipse-temurin:%s-jre

            WORKDIR /app
            COPY app.jar app.jar
            EXPOSE %d

            ENTRYPOINT ["java", "-jar", "/app/app.jar"]
            """.formatted(javaVersion, applicationPort);

        return IaCFileDTO.FileContentResDTO.builder()
            .fileName("Dockerfile")
            .content(content)
            .build();
    }
}
