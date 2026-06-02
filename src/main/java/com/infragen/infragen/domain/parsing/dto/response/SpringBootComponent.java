package com.infragen.infragen.domain.parsing.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;

@Getter
@JsonPropertyOrder({ "nodeId", "positionX", "positionY", "port", "env" })
public class SpringBootComponent extends BaseComponent{
    private int port;
    private String env;

    public SpringBootComponent(String id, float posX, float posY, int port , String env) {
        super(id, posX, posY);
        this.port = port;
        this.env = env;
    }
}