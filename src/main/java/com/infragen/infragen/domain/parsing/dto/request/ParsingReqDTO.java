package com.infragen.infragen.domain.parsing.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class ParsingReqDTO {
    private Long projectId;
    private List<NodeDTO> nodes;
    private List<EdgeDTO> edges;
}