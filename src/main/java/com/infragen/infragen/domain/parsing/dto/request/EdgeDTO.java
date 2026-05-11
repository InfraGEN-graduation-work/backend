package com.infragen.infragen.domain.parsing.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EdgeDTO {
    private String edgeId;
    private String sourceNodeId;
    private String targetNodeId;
    private String connectionType;
}