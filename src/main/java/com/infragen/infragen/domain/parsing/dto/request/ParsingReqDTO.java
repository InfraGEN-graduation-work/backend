package com.infragen.infragen.domain.parsing.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class ParsingReqDTO {
    private Long projectId;

    @Valid
    @NotNull(message = "노드 리스트가 비어있습니다.")
    private List<NodeDTO> nodes;

    private List<EdgeDTO> edges;
}