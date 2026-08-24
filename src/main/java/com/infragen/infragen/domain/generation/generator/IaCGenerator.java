package com.infragen.infragen.domain.generation.generator;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;

public interface IaCGenerator {
    OutputFormat getOutputFormat();

    IaCFileDTO.BundleResDTO generate(ParsingResultDTO parsingResult);
}
