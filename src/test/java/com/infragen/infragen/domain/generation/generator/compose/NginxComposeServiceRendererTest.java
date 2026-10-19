package com.infragen.infragen.domain.generation.generator.compose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.NginxComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NginxComposeServiceRendererTest {

    @Test
    @DisplayName("앱 연결이 없으면 내부 컴포넌트 상태 오류로 거부한다")
    void additionalFiles_MissingApplication_ThrowsInvalidComponentState() {
        // given
        var nginx = NginxComponent.builder().id("proxy").build();
        var parsingResult = new ParsingResultDTO();
        parsingResult.setComponents(List.of(nginx));
        var context = new ComposeGenerationContext(parsingResult);
        var renderer = new NginxComposeServiceRenderer();

        // when
        var exception = assertThrows(IaCGenerationException.class,
            () -> renderer.additionalFiles(nginx, context));

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
    }
}
