package com.infragen.infragen.domain.parsing.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NodeDTO {
    private String nodeId;
    private String componentType;
    private Float positionX;
    private Float positionY;
    private Map<String, Object> properties;
}