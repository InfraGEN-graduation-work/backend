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
    private List<SpringBoot> SpringBoot = new ArrayList<>();
    private List<MySQL> MySQL = new ArrayList<>();
}