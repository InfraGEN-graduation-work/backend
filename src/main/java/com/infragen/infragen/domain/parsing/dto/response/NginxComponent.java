package com.infragen.infragen.domain.parsing.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.infragen.infragen.global.enums.ComponentType;

import lombok.Builder;
import lombok.Getter;

@Getter
@JsonPropertyOrder({
        "nodeId", "positionX", "positionY", "componentType", "imageVersion",
        "containerName", "port", "configVolumeName", "webRootVolumeName"
})
public class NginxComponent extends BaseComponent {
    private String imageVersion;
    private String containerName;
    private int port;
    private String configVolumeName;
    private String webRootVolumeName;

    @Builder
    public NginxComponent(
            String id,
            float posX,
            float posY,
            String imageVersion,
            String containerName,
            int port,
            String configVolumeName,
            String webRootVolumeName
    ) {
        super(id, posX, posY, ComponentType.NGINX);
        this.imageVersion = imageVersion;
        this.containerName = containerName;
        this.port = port;
        this.configVolumeName = configVolumeName;
        this.webRootVolumeName = webRootVolumeName;
    }
}