package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.global.enums.ComponentType;
import java.util.List;
import java.util.Optional;

// ComponentParser와 대칭 — 타입별 Docker Compose에 services: 블록 생성
public interface ComposeServiceRenderer {
    ComponentType getSupportedType();

    String render(BaseComponent component, ComposeGenerationContext context);

    default List<IaCFileDTO.FileContentResDTO> additionalFiles(
        BaseComponent component, ComposeGenerationContext context) {
        return List.of();
    }

    /**
     * LOCAL_DEV 안내 문서 {@code LOCAL_DEV_GUIDE.md}에 넣을 이 service의 섹션을 돌려준다.
     *
     * <p>섹션은 {@code ##} 제목으로 시작하는 Markdown이다. {@code .env}에는 그래프에 입력한 값이 들어 있지만 안내에는 변수 이름만 쓰고 값은 쓰지 않는다.
     * {@link #render}와 같은 component로 호출한다.
     *
     * @return 안내 섹션. 안내할 내용이 없으면 빈 값
     */
    default Optional<String> guideSection(BaseComponent component, ComposeGenerationContext context) {
        return Optional.empty();
    }
}
