package com.infragen.infragen.domain.generation.generator.compose;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("envValueCases")
    @DisplayName(".env 값은 특수 문자가 있을 때만 큰따옴표로 감싸고 \\, \", $를 이스케이프한다")
    void escapeEnvValue_SerializesSpecialCharacters(String scenario, String value, String expected) {
        // when
        String escaped = ComposeYamlSupport.escapeEnvValue(value);

        // then
        assertEquals(expected, escaped);
    }

    private static Stream<Arguments> envValueCases() {
        return Stream.of(
            Arguments.of("null은 빈 문자열", null, ""),
            Arguments.of("안전한 값은 그대로", "abcd1234", "abcd1234"),
            Arguments.of("공백", "pass word1", "\"pass word1\""),
            Arguments.of("#", "pass#word1", "\"pass#word1\""),
            Arguments.of("$는 $$", "pa$word12", "\"pa$$word12\""),
            Arguments.of("${} 보간 표기", "pa${HOME}x12", "\"pa$${HOME}x12\""),
            Arguments.of("큰따옴표로 시작", "\"abcd1234", "\"\\\"abcd1234\""),
            Arguments.of("안쪽 큰따옴표", "ab\"cd1234", "\"ab\\\"cd1234\""),
            Arguments.of("작은따옴표", "it's1234pw", "\"it's1234pw\""),
            Arguments.of("백슬래시", "back\\slash12", "\"back\\\\slash12\""),
            Arguments.of("끝이 백슬래시", "abcd123\\", "\"abcd123\\\\\""),
            Arguments.of("백슬래시 뒤 작은따옴표", "a\\'bcd1234", "\"a\\\\'bcd1234\""),
            Arguments.of("백틱", "ab`cd1234", "\"ab`cd1234\"")
        );
    }
}
