package com.infragen.infragen.domain.parsing.dto.response;

import java.util.ArrayList;
import java.util.List;

import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParsingResultDTO {
    private Long projectId;
    private List<BaseComponent> components = new ArrayList<>();
    private List<EdgeDTO> edges = new ArrayList<>();
}
