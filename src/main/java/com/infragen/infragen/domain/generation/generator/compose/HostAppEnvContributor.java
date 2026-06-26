package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.global.enums.ComponentType;

// LOCAL_DEV — 호스트에서 실행할 애플리케이션용 .env 키와 값을 context에 추가
public interface HostAppEnvContributor {

    ComponentType getDependencyType();

    void contributeHostAppEnv(
        BaseComponent dependency,
        BaseComponent application,
        ComposeGenerationContext ctx
    );
}
