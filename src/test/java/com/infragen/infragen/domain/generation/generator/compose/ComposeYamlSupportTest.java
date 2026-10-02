package com.infragen.infragen.domain.generation.generator.compose;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Compose renderer 공통 유틸")
class ComposeYamlSupportTest {

    @ParameterizedTest(name = "containerName=[{0}] → {1}")
    @CsvSource(value = {
        "my-mysql, my-mysql",
        "'  my-mysql  ', my-mysql",
        "'', mysql",
        "'   ', mysql",
        "NULL, mysql"
    }, nullValues = "NULL")
    @DisplayName("containerName이 비어 있으면 service 키를 쓰고, 값이 있으면 trim한 값을 쓴다")
    void resolveContainerName_ReturnsTrimmedNameOrServiceName(String containerName, String expected) {
        // given
        String serviceName = "mysql";

        // when
        String resolved = ComposeYamlSupport.resolveContainerName(containerName, serviceName);

        // then
        assertEquals(expected, resolved);
    }

    @ParameterizedTest(name = "imageVersion=[{0}] → {1}")
    @CsvSource(value = {
        "mysql:8.4, mysql:8.4",
        "'  mysql:8.4  ', mysql:8.4",
        "'', mysql:8.0",
        "'   ', mysql:8.0",
        "NULL, mysql:8.0"
    }, nullValues = "NULL")
    @DisplayName("imageVersion이 비어 있으면 기본 이미지를 쓰고, 값이 있으면 trim한 값을 쓴다")
    void resolveImage_ReturnsTrimmedImageOrDefault(String imageVersion, String expected) {
        // given
        String defaultImage = "mysql:8.0";

        // when
        String resolved = ComposeYamlSupport.resolveImage(imageVersion, defaultImage);

        // then
        assertEquals(expected, resolved);
    }
}
