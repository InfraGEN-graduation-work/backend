package com.infragen.infragen.domain.parsing.dto.response;

import com.infragen.infragen.global.enums.ComponentType;

import lombok.Builder;
import lombok.Getter;

@Getter
public class MySQLComponent extends BaseComponent implements VolumeComponent {
    private String imageVersion;
    private String containerName;
    private MySQLEnvComponent env;
    private int port;
    private String volumeName;

    @Builder
    public MySQLComponent(
        String id,
        float posX,
        float posY,
        String imageVersion,
        String containerName,
        MySQLEnvComponent env,
        int port,
        String volumeName
    ) {
        super(id, posX, posY, ComponentType.MYSQL);
        this.imageVersion = imageVersion;
        this.containerName = containerName;
        this.env = env;
        this.port = port;
        this.volumeName = volumeName;
    }
}
