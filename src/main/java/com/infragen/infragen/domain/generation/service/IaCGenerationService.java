package com.infragen.infragen.domain.generation.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.generator.IaCGenerator;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class IaCGenerationService {
    private final Map<OutputFormat, IaCGenerator> generatorMap;

    public IaCGenerationService(List<IaCGenerator> generators) {
        this.generatorMap = generators.stream()
            .collect(Collectors.toMap(IaCGenerator::getOutputFormat, generator -> generator));
    }

    public IaCFileDTO.BundleResDTO generate(ParsingResultDTO parsingResult, OutputFormat outputFormat) {
        IaCGenerator generator = generatorMap.get(outputFormat);
        if (generator == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.UNSUPPORTED_OUTPUT_FORMAT);
        }
        return generator.generate(parsingResult);
    }

}
