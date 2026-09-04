package com.infragen.infragen.domain.generation.service.command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.generation.converter.GenerationRequestConverter;
import com.infragen.infragen.domain.generation.dto.request.DeploymentTargetReqDTO;
import com.infragen.infragen.domain.generation.dto.request.GenerateReqDTO;
import com.infragen.infragen.domain.generation.dto.response.GenerateResDTO;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.enums.DeploymentOption;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.generation.service.IaCGenerationService;
import com.infragen.infragen.domain.generation.validator.DeploymentTargetValidator;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.service.ParsingService;
import com.infragen.infragen.domain.project.service.command.ProjectHistoryCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationCommandService {
    private final ProjectQueryService projectQueryService;
    private final ParsingService parsingService;
    private final IaCGenerationService iaCGenerationService;
    private final ProjectHistoryCommandService projectHistoryCommandService;
    private final DeploymentTargetValidator deploymentTargetValidator;

    /**
     * 배포 옵션에 따라 Local 또는 선택한 Cloud provider 산출물을 생성하고 하나의 history에 저장한다.
     *
     * @param projectId 프로젝트 식별자
     * @param request 배포 옵션과 캔버스 그래프 요청
     * @param memberId 요청 회원 식별자
     * @return 생성 파일과 history 식별자
     */
    @Transactional
    public GenerateResDTO.GenerateResultResDTO generate(
        Long projectId,
        GenerateReqDTO.Request request,
        Long memberId
    ) {
        validateRequest(request);
        return generateAndSave(projectId, GenerationRequestConverter.toParsingRequest(request), memberId,
            parsingResult -> generateBundle(request, parsingResult));
    }

    private IaCFileDTO.BundleResDTO generateBundle(
        GenerateReqDTO.Request request,
        ParsingResultDTO parsingResult
    ) {
        DeploymentOption deploymentOption = request.deploymentOption();

        if (deploymentOption == DeploymentOption.LOCAL) {
            return iaCGenerationService.generate(parsingResult, OutputFormat.DOCKER_COMPOSE);
        }

        IaCFileDTO.BundleResDTO cloudBundle = iaCGenerationService.generate(
            parsingResult,
            OutputFormat.TERRAFORM,
            request.deploymentTarget()
        );

        if (!Boolean.TRUE.equals(request.includeLocalSpec())) {
            return cloudBundle;
        }

        List<IaCFileDTO.FileContentResDTO> files = new ArrayList<>(cloudBundle.files());
        files.addAll(iaCGenerationService.generate(
            parsingResult,
            OutputFormat.DOCKER_COMPOSE
        ).files());
        return IaCFileDTO.BundleResDTO.builder()
            .files(List.copyOf(files))
            .build();
    }

    private void validateRequest(GenerateReqDTO.Request request) {
        if (request == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_GENERATION_REQUEST);
        }

        DeploymentOption deploymentOption = request.deploymentOption();

        if (deploymentOption == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.MISSING_DEPLOYMENT_OPTION);
        }

        if (deploymentOption == DeploymentOption.LOCAL) {
            if (!Boolean.FALSE.equals(request.includeLocalSpec()) // canonical Local 요청은 false만 허용
                || request.deploymentTarget() != null) {
                throw new IaCGenerationException(
                    IaCGenerationErrorCode.INVALID_LOCAL_DEPLOYMENT_CONFIGURATION);
            }
            return;
        }

        validateCloudTarget(deploymentOption, request.deploymentTarget());
    }

    private GenerateResDTO.GenerateResultResDTO generateAndSave(
        Long projectId,
        ParsingReqDTO request,
        Long memberId,
        Function<ParsingResultDTO, IaCFileDTO.BundleResDTO> bundleGenerator
    ) {
        log.info("인프라 코드 생성 요청: projectId={}, memberId={}", projectId, memberId);

        projectQueryService.getOwnedProject(projectId, memberId);
        ParsingResultDTO parsingResult = parsingService.parsing(request, projectId);
        IaCFileDTO.BundleResDTO bundle = bundleGenerator.apply(parsingResult);

        return saveGeneratedResult(projectId, memberId, bundle);
    }

    private void validateCloudTarget(
        DeploymentOption deploymentOption,
        DeploymentTargetReqDTO.Target deploymentTarget
    ) {
        if (deploymentTarget == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.MISSING_DEPLOYMENT_TARGET);
        }

        boolean validTarget = deploymentTarget.provider() == deploymentOption;

        if (!validTarget) {
            throw new IaCGenerationException(
                IaCGenerationErrorCode.DEPLOYMENT_TARGET_PROVIDER_MISMATCH);
        }
        deploymentTargetValidator.validate(deploymentTarget);
    }

    private GenerateResDTO.GenerateResultResDTO saveGeneratedResult(
        Long projectId,
        Long memberId,
        IaCFileDTO.BundleResDTO bundle
    ) {
        Long historyId = projectHistoryCommandService.saveGeneratedHistory(
            projectId, memberId, bundle.files());

        log.info("인프라 코드 생성 완료: projectId={}, fileCount={}, historyId={}",
            projectId, bundle.files().size(), historyId);

        return GenerateResDTO.GenerateResultResDTO.builder()
            .files(bundle.files().stream()
                .map(file -> GenerateResDTO.GeneratedFileResDTO.builder()
                    .fileName(file.fileName())
                    .content(file.content())
                    .build())
                .toList())
            .historyId(historyId)
            .build();
    }

}
