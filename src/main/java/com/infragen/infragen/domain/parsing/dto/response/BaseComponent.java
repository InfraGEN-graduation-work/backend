package com.infragen.infragen.domain.parsing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BaseComponent {
    private String nodeId;
    private float positionX;
    private float positionY;

}