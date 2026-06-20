package com.infragen.infragen.domain.parsing.dto.request;

import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ParsingResultDTO {
    private Long projectId;
    private List<SpringBootComponent> springBoot = new ArrayList<>();
    private List<MySQLComponent> mySQL = new ArrayList<>();
    private List<EdgeDTO> edges = new ArrayList<>();
}