package com.infragen.infragen.domain.generation.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.generation.dto.request.DeploymentTargetReqDTO;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.generation.generator.LocalIaCGenerator;
import com.infragen.infragen.domain.generation.generator.TargetAwareIaCGenerator;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;

@DisplayName("IaCGenerationService")
class IaCGenerationServiceTest {

    @Test
    @DisplayName("Local 출력 — Local generator registry를 호출")
    void generate_Local_UsesLocalGenerator() {
        // given
        LocalIaCGenerator localGenerator = mock(LocalIaCGenerator.class);
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        IaCFileDTO.BundleResDTO bundle = IaCFileDTO.BundleResDTO.builder()
            .files(List.of())
            .build();
        when(localGenerator.getOutputFormat()).thenReturn(OutputFormat.DOCKER_COMPOSE);
        when(localGenerator.generate(parsingResult)).thenReturn(bundle);
        IaCGenerationService service = new IaCGenerationService(
            List.of(localGenerator),
            List.of()
        );

        // when
        IaCFileDTO.BundleResDTO result = service.generate(
            parsingResult,
            OutputFormat.DOCKER_COMPOSE
        );

        // then
        assertSame(bundle, result);
        verify(localGenerator).generate(parsingResult);
    }

    @Test
    @DisplayName("Cloud 출력 — target-aware generator registry를 호출")
    void generate_Cloud_UsesTargetAwareGenerator() {
        // given
        TargetAwareIaCGenerator cloudGenerator = mock(TargetAwareIaCGenerator.class);
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        DeploymentTargetReqDTO.Target deploymentTarget = new DeploymentTargetReqDTO.AwsDeploymentTarget(
            null, null, null, null, null, null,
            null, null, null, null, null, null, null
        );
        IaCFileDTO.BundleResDTO bundle = IaCFileDTO.BundleResDTO.builder()
            .files(List.of())
            .build();
        when(cloudGenerator.getOutputFormat()).thenReturn(OutputFormat.TERRAFORM);
        when(cloudGenerator.generate(parsingResult, deploymentTarget)).thenReturn(bundle);
        IaCGenerationService service = new IaCGenerationService(
            List.of(),
            List.of(cloudGenerator)
        );

        // when
        IaCFileDTO.BundleResDTO result = service.generate(
            parsingResult,
            OutputFormat.TERRAFORM,
            deploymentTarget
        );

        // then
        assertSame(bundle, result);
        verify(cloudGenerator).generate(parsingResult, deploymentTarget);
    }
}
