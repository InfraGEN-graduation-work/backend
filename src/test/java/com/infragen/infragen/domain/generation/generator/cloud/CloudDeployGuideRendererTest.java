package com.infragen.infragen.domain.generation.generator.cloud;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;

@DisplayName("CLOUD_DEPLOY 통합 안내 문서 renderer")
class CloudDeployGuideRendererTest {

    private final CloudDeployGuideRenderer renderer = new CloudDeployGuideRenderer();

    @Test
    @DisplayName("섹션을 받은 순서대로 제목과 경고 문서 안내 다음에 넣는다")
    void render_WithSections_JoinsSectionsInOrder() {
        // given
        List<String> sections = List.of("## 첫 섹션\n\n첫 내용\n", "## 둘째 섹션\n\n둘째 내용\n");

        // when
        Optional<IaCFileDTO.FileContentResDTO> guide = renderer.render(sections);

        // then
        String content = guide.orElseThrow().content();
        assertAll(
            () -> assertEquals("CLOUD_DEPLOY_GUIDE.md", guide.orElseThrow().fileName()),
            () -> assertTrue(content.startsWith("# CLOUD_DEPLOY 배포 안내\n")),
            () -> assertTrue(content.contains("`CLOUD_DEPLOY_WARNING.md`의 안내를 먼저 확인해 주세요.")),
            () -> assertTrue(content.endsWith("\n\n## 첫 섹션\n\n첫 내용\n\n## 둘째 섹션\n\n둘째 내용\n"))
        );
    }

    @Test
    @DisplayName("섹션이 없으면 안내 문서를 만들지 않는다")
    void render_WithoutSections_ReturnsEmpty() {
        // given
        List<String> sections = List.of();

        // when
        Optional<IaCFileDTO.FileContentResDTO> guide = renderer.render(sections);

        // then
        assertTrue(guide.isEmpty());
    }
}
