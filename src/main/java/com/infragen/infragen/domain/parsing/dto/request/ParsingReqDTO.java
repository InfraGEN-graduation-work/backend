package com.infragen.infragen.domain.parsing.dto.request;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class ParsingReqDTO {
    private final List<NodeDTO> nodes;
    private final List<EdgeDTO> edges;
}
