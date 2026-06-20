package com.infragen.infragen.domain.parsing.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;

@Getter
@JsonPropertyOrder({ "nodeId", "positionX", "positionY", "name" , "port"})
public class SpringBootComponent extends BaseComponent{
    private String name;
    private int port;
    @Builder
    public SpringBootComponent(String id, float posX, float posY, String name , int port) {
        super(id, posX, posY);
        this.name = name;
        this.port = port;
    }
}