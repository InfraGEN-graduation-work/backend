package com.infragen.infragen.domain.generation.generator.cloud;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;

class CloudDeployWarningRendererTest {

    private final CloudDeployWarningRenderer renderer = new CloudDeployWarningRenderer();

    @Test
    @DisplayName("전달받은 앱 파일 문구를 준비물 안내에 넣는다")
    void render_requiredAppFiles_insertsIntoPreparationNotice() {
        // when
        IaCFileDTO.FileContentResDTO warning = renderer.render("`app.jar`");

        // then
        assertAll(
            () -> assertEquals("CLOUD_DEPLOY_WARNING.md", warning.fileName()),
            () -> assertTrue(warning.content().contains("외부 `.env`, `app.jar`, provider 자격증명을"))
        );
    }
}
