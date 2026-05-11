package com.infragen.infragen.domain.parsing.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.databind.JsonNode;

@Getter
@Setter
public class NodeDTO {
    private String nodeId;
    private String componentType;
    private Float positionX;
    private Float positionY;
    private Object properties;
}