package com.infragen.infragen.domain.parsing.dto.request;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class ParsingReqDTO {
    private List<NodeDTO> nodes;
    private List<EdgeDTO> edges;
}
