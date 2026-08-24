package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.global.enums.ComponentType;

// ComponentParser와 대칭 — 타입별 Docker Compose에 services: 블록 생성
public interface ComposeServiceRenderer {
    ComponentType getSupportedType();

    String render(BaseComponent component, ComposeGenerationContext context);
}
