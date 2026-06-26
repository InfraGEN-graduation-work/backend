package com.infragen.infragen.domain.generation.generator.compose;

import java.util.List;

import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.global.enums.ComponentType;

// 앱이 DB에 의존할 때 JDBC 연결 정보와 credential env를 context에 추가
public interface DatabaseEnvContributor {
    ComponentType getDatabaseType();

    void contributeConnection(
        BaseComponent db,
        BaseComponent app,
        ComposeGenerationContext ctx,
        List<String> envKeys
    );
}
