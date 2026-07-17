package com.infragen.infragen.domain.parsing.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.infragen.infragen.global.enums.ComponentType;

import lombok.Builder;
import lombok.Getter;

@Getter
@JsonPropertyOrder({
        "nodeId", "positionX", "positionY", "componentType", "imageVersion",
        "containerName", "password", "port", "volumeName"
})
public class RedisComponent extends BaseComponent {
    private String imageVersion;
    private String containerName;
    private String password;
    private int port;
    private String volumeName;

    @Builder
    public RedisComponent(
            String id,
            float posX,
            float posY,
            String imageVersion,
            String containerName,
            String password,
            int port,
            String volumeName
    ) {
        super(id, posX, posY, ComponentType.REDIS);
        this.imageVersion = imageVersion;
        this.containerName = containerName;
        this.password = password;
        this.port = port;
        this.volumeName = volumeName;
    }
}