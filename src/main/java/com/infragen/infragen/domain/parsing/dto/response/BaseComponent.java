package com.infragen.infragen.domain.parsing.dto.response;

import com.infragen.infragen.global.enums.ComponentType;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BaseComponent {
    private String nodeId;
    private float positionX;
    private float positionY;
    private ComponentType componentType;
}
