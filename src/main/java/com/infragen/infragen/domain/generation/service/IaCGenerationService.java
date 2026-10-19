package com.infragen.infragen.domain.generation.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.dto.request.DeploymentTargetReqDTO;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.generator.LocalIaCGenerator;
import com.infragen.infragen.domain.generation.generator.TargetAwareIaCGenerator;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.global.enums.ComponentType;

import lombok.extern.slf4j.Slf4j;

/** Local 또는 target-aware generator를 출력 형식에 따라 선택하는 application service다. */
@Service
@Slf4j
public class IaCGenerationService {
    private final Map<OutputFormat, LocalIaCGenerator> localGeneratorMap;
    private final Map<OutputFormat, TargetAwareIaCGenerator> targetAwareGeneratorMap;

    /**
     * generator 구현체를 capability별 registry로 구성한다.
     *
     * @param localGenerators target 없이 Local 산출물을 생성하는 generator 목록
     * @param targetAwareGenerators deployment target이 필요한 generator 목록
     */
    public IaCGenerationService(
        List<LocalIaCGenerator> localGenerators,
        List<TargetAwareIaCGenerator> targetAwareGenerators
    ) {
        this.localGeneratorMap = localGenerators.stream()
            .collect(Collectors.toMap(
                LocalIaCGenerator::getOutputFormat,
                generator -> generator
            ));
        this.targetAwareGeneratorMap = targetAwareGenerators.stream()
            .collect(Collectors.toMap(
                TargetAwareIaCGenerator::getOutputFormat,
                generator -> generator
            ));
    }

    /**
     * target 없는 Local generator를 출력 형식으로 선택해 실행한다.
     *
     * @param parsingResult 파싱된 runtime graph
     * @param outputFormat 생성할 출력 형식
     * @return 생성 파일 bundle
     * @throws IaCGenerationException 지원하는 Local generator가 없을 때
     */
    public IaCFileDTO.BundleResDTO generate(ParsingResultDTO parsingResult, OutputFormat outputFormat) {
        LocalIaCGenerator generator = localGeneratorMap.get(outputFormat);
        if (generator == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.UNSUPPORTED_OUTPUT_FORMAT);
        }
        return generator.generate(parsingResult);
    }

    /**
     * Local generator가 해당 타입을 산출물에 담는지 확인한다.
     *
     * @param outputFormat 확인할 Local 출력 형식
     * @param componentType 확인할 component type
     * @return 산출물에 담을 수 있으면 {@code true}
     * @throws IaCGenerationException 지원하는 Local generator가 없을 때
     */
    public boolean supportsLocal(OutputFormat outputFormat, ComponentType componentType) {
        LocalIaCGenerator generator = localGeneratorMap.get(outputFormat);
        if (generator == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.UNSUPPORTED_OUTPUT_FORMAT);
        }
        return generator.supports(componentType);
    }

    /**
     * target-aware generator를 출력 형식으로 선택해 실행한다.
     *
     * @param parsingResult 파싱된 runtime graph
     * @param outputFormat 생성할 출력 형식
     * @param deploymentTarget 선택한 Cloud provider 배포 설정
     * @return 생성 파일 bundle
     * @throws IaCGenerationException 지원하는 target-aware generator가 없을 때
     */
    public IaCFileDTO.BundleResDTO generate(
        ParsingResultDTO parsingResult,
        OutputFormat outputFormat,
        DeploymentTargetReqDTO.Target deploymentTarget
    ) {
        TargetAwareIaCGenerator generator = targetAwareGeneratorMap.get(outputFormat);
        if (generator == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.UNSUPPORTED_OUTPUT_FORMAT);
        }
        return generator.generate(parsingResult, deploymentTarget);
    }

}
