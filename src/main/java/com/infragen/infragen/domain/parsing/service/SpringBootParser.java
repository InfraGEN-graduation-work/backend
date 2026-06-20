package com.infragen.infragen.domain.parsing.service;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;
import com.infragen.infragen.domain.parsing.enums.ComponentType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class SpringBootParser implements ComponentParser{
    @Override
    public ComponentType getSupportedType(){
        return ComponentType.SPRING_BOOT;
    }

    @Override
    public void parseAndAddToResult(NodeDTO node, JsonNode props, int port, ParsingResultDTO result) {
        String name = props.path("name").asString();

        SpringBootComponent springBoot = SpringBootComponent.builder()
                .id(node.getNodeId())
                .posX(node.getPositionX())
                .posY(node.getPositionY())
                .name(name)
                .port(port)
                .build();

        result.getSpringBoot().add(springBoot);
    }
}
