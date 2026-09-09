package com.infragen.infragen.domain.generation.generator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.dto.request.DeploymentTargetReqDTO;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.enums.DeploymentOption;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.generation.generator.cloud.CloudComposeRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.CloudDeployContext;
import com.infragen.infragen.domain.generation.generator.cloud.CloudDeployFileAssembler;
import com.infragen.infragen.domain.generation.generator.cloud.CloudDeployWarningRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.CloudTerraformRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.RuntimeDockerfileRenderer;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;

/**
 * 파싱된 그래프를 CLOUD_DEPLOY 산출물 묶음으로 조립한다.
 * provider와 runtime별 렌더링은 하위 renderer에 위임한다.
 */
@Component
public class TerraformIaCGenerator implements TargetAwareIaCGenerator {
    private final Map<DeploymentOption, CloudTerraformRenderer>
        terraformRendererMap;
    private final RuntimeDockerfileRenderer runtimeDockerfileRenderer =
        new RuntimeDockerfileRenderer();
    private final CloudComposeRenderer cloudComposeRenderer;
    private final CloudDeployWarningRenderer cloudDeployWarningRenderer =
        new CloudDeployWarningRenderer();
    private final CloudDeployFileAssembler cloudDeployFileAssembler =
        new CloudDeployFileAssembler();

    /**
     * provider key를 기준으로 Terraform renderer registry를 구성한다.
     *
     * @param cloudComposeRenderer Cloud runtime Compose renderer
     * @param terraformRenderers provider별 Terraform renderer 목록
     * @throws IllegalStateException 같은 provider renderer가 중복 등록된 경우
     */
    public TerraformIaCGenerator(
        CloudComposeRenderer cloudComposeRenderer,
        List<CloudTerraformRenderer> terraformRenderers
    ) {
        this.cloudComposeRenderer = cloudComposeRenderer;
        this.terraformRendererMap = new EnumMap<>(DeploymentOption.class);
        for (CloudTerraformRenderer renderer : terraformRenderers) {
            CloudTerraformRenderer previous = terraformRendererMap.put(
                renderer.getProvider(), renderer);

            if (previous != null) {
                throw new IllegalStateException(
                    "중복된 cloud Terraform renderer입니다: " + renderer.getProvider());
            }
        }
    }

    /** @return 내부 generator 분류용 Terraform 출력 형식 */
    @Override
    public OutputFormat getOutputFormat() {
        return OutputFormat.TERRAFORM;
    }

    /**
     * deployment target의 provider renderer만 선택해 Cloud bundle을 생성한다.
     *
     * @param parsingResult 파싱된 runtime graph
     * @param deploymentTarget 선택한 Cloud provider 배포 설정
     * @return Cloud scope가 적용된 생성 파일 bundle
     * @throws IaCGenerationException target이 없거나 지원 renderer가 없는 경우
     */
    @Override
    public IaCFileDTO.BundleResDTO generate(
        ParsingResultDTO parsingResult,
        DeploymentTargetReqDTO.Target deploymentTarget
    ) {
        CloudDeployContext context = CloudDeployContext.from(parsingResult);
        return assembleCloudBundle(context, renderersFor(deploymentTarget), deploymentTarget);
    }

    private IaCFileDTO.BundleResDTO assembleCloudBundle(
        CloudDeployContext context,
        Collection<CloudTerraformRenderer> terraformRenderers,
        DeploymentTargetReqDTO.Target deploymentTarget
    ) {
        List<IaCFileDTO.FileContentResDTO> files = new ArrayList<>();

        for (CloudTerraformRenderer renderer : terraformRenderers) {
            files.addAll(renderer.render(context, deploymentTarget));
        }

        files.addAll(List.of(
            runtimeDockerfileRenderer.render(context.javaVersion(), context.applicationPort()),
            cloudComposeRenderer.render(context),
            cloudDeployWarningRenderer.render()
        ));

        return cloudDeployFileAssembler.assemble(files);
    }

    private Collection<CloudTerraformRenderer> renderersFor(
        DeploymentTargetReqDTO.Target deploymentTarget
    ) {
        if (deploymentTarget == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }

        CloudTerraformRenderer renderer = terraformRendererMap.get(deploymentTarget.provider());
        if (renderer == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }
        return List.of(renderer);
    }
}
