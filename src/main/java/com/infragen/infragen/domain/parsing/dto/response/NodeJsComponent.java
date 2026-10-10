package com.infragen.infragen.domain.parsing.dto.response;

import com.infragen.infragen.global.enums.ComponentType;

import lombok.Builder;
import lombok.Getter;

/**
 * NODE_JS 애플리케이션 노드의 파싱 결과다.
 *
 * <p>실행 명령은 속성으로 받지 않고 {@code npm start}로 고정한다. 사용자 문자열을 Dockerfile에 넣지 않으려는 결정이다.
 */
@Getter
public class NodeJsComponent extends ApplicationComponent {
    private String name;
    private int port;
    private String nodeVersion;
    private String containerName;

    @Builder
    public NodeJsComponent(
        String id,
        float posX,
        float posY,
        String name,
        int port,
        String nodeVersion,
        String containerName
    ) {
        super(id, posX, posY, ComponentType.NODE_JS);
        this.name = name;
        this.port = port;
        this.nodeVersion = nodeVersion;
        this.containerName = containerName;
    }
}
