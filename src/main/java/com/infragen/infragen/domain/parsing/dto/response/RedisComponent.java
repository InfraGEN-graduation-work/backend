package com.infragen.infragen.domain.parsing.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.infragen.infragen.global.enums.ComponentType;

import lombok.Builder;
import lombok.Getter;

@Getter
@JsonPropertyOrder({
    "nodeId", "positionX", "positionY", "componentType", "imageVersion",
    "containerName", "port", "volumeName", "password"
})
public class RedisComponent extends BaseComponent implements VolumeComponent {
    private String imageVersion;
    private String containerName;
    private int port;
    private String volumeName;
    private String password;

    @Builder
    public RedisComponent(
        String id,
        float posX,
        float posY,
        String imageVersion,
        String containerName,
        int port,
        String volumeName,
        String password
    ) {
        super(id, posX, posY, ComponentType.REDIS);
        this.imageVersion = imageVersion;
        this.containerName = containerName;
        this.port = port;
        this.volumeName = volumeName;
        this.password = password;
    }
}
