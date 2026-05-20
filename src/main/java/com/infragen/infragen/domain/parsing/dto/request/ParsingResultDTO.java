package com.infragen.infragen.domain.parsing.dto.request;

import com.infragen.infragen.domain.parsing.dto.response.MySQL;
import com.infragen.infragen.domain.parsing.dto.response.SpringBoot;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ParsingResultDTO {
    private Long projectId;
    private List<SpringBoot> springBoot = new ArrayList<>();
    private List<MySQL> mySQL = new ArrayList<>();
}