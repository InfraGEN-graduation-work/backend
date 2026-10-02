package com.infragen.infragen.domain.parsing.dto.response;

import com.infragen.infragen.global.enums.ComponentType;

import lombok.Builder;
import lombok.Getter;

@Getter
public class SpringBootComponent extends BaseComponent {
    private String name;
    private int port;
    private String javaVersion;
    private String containerName;

    @Builder
    public SpringBootComponent(
        String id,
        float posX,
        float posY,
        String name,
        int port,
        String javaVersion,
        String containerName
    ) {
        super(id, posX, posY, ComponentType.SPRING_BOOT);
        this.name = name;
        this.port = port;
        this.javaVersion = javaVersion;
        this.containerName = containerName;
    }
}
