package com.infragen.infragen.domain.parsing.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NodeDTO {
    private String nodeId;
    private String componentType;
    private Float positionX;
    private Float positionY;
    private Object properties;
}