package com.infragen.infragen.domain.parsing.dto.response;

import com.infragen.infragen.global.enums.ComponentType;
import lombok.Builder;
import lombok.Getter;

@Getter
public class NginxComponent extends BaseComponent {
    private final String imageVersion;
    private final String containerName;
    private final int port;

    @Builder
    public NginxComponent(String id, float posX, float posY, String imageVersion,
        String containerName, int port) {
        super(id, posX, posY, ComponentType.NGINX);
        this.imageVersion = imageVersion;
        this.containerName = containerName;
        this.port = port;
    }
}
