package com.infragen.infragen.domain.parsing.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.infragen.infragen.domain.parsing.dto.response.env.MongoDBEnvComponent;
import com.infragen.infragen.global.enums.ComponentType;

import lombok.Builder;
import lombok.Getter;

@Getter
@JsonPropertyOrder({
        "nodeId", "positionX", "positionY", "componentType", "imageVersion",
        "containerName", "env", "port", "volumeName"
})
public class MongoDBComponent extends BaseComponent {
    private String imageVersion;
    private String containerName;
    private MongoDBEnvComponent env;
    private int port;
    private String volumeName;

    @Builder
    public MongoDBComponent(
            String id,
            float posX,
            float posY,
            String imageVersion,
            String containerName,
            MongoDBEnvComponent env,
            int port,
            String volumeName
    ) {
        super(id, posX, posY, ComponentType.MONGODB);
        this.imageVersion = imageVersion;
        this.containerName = containerName;
        this.env = env;
        this.port = port;
        this.volumeName = volumeName;
    }
}