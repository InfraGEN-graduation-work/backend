package com.infragen.infragen.domain.generation.generator;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.generation.generator.cloud.AwsTerraformRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.CloudComposeRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.CloudDeployContext;
import com.infragen.infragen.domain.generation.generator.cloud.CloudDeployFileAssembler;
import com.infragen.infragen.domain.generation.generator.cloud.CloudDeployWarningRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.MysqlCloudComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.OciTerraformRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.RuntimeDockerfileRenderer;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;

/**
 * 파싱된 그래프를 CLOUD_DEPLOY 산출물 묶음으로 조립한다.
 * provider와 runtime별 렌더링은 하위 renderer에 위임한다.
 */
@Component
public class TerraformIaCGenerator implements IaCGenerator {
    private final AwsTerraformRenderer awsTerraformRenderer = new AwsTerraformRenderer();
    private final OciTerraformRenderer ociTerraformRenderer = new OciTerraformRenderer();
    private final RuntimeDockerfileRenderer runtimeDockerfileRenderer =
        new RuntimeDockerfileRenderer();
    private final CloudComposeRenderer cloudComposeRenderer;
    private final CloudDeployWarningRenderer cloudDeployWarningRenderer =
        new CloudDeployWarningRenderer();
    private final CloudDeployFileAssembler cloudDeployFileAssembler =
        new CloudDeployFileAssembler();

    @Autowired
    public TerraformIaCGenerator(CloudComposeRenderer cloudComposeRenderer) {
        this.cloudComposeRenderer = cloudComposeRenderer;
    }

    public TerraformIaCGenerator() {
        this(new CloudComposeRenderer(List.of(new MysqlCloudComposeServiceRenderer())));
    }

    @Override
    public OutputFormat getOutputFormat() {
        return OutputFormat.TERRAFORM;
    }

    /**
     * 검증된 파싱 결과로 provider별 Terraform과 runtime 산출물을 생성한다.
     * 입력 그래프의 포트·이미지 버전은 `CloudDeployContext`를 통해 renderer에 전달한다.
     *
     * @param parsingResult ParsingService가 반환한 그래프 결과
     * @return CLOUD_DEPLOY 생성 파일 묶음
     */
    @Override
    public IaCFileDTO.BundleResDTO generate(ParsingResultDTO parsingResult) {
        CloudDeployContext context = CloudDeployContext.from(parsingResult);
        List<IaCFileDTO.FileContentResDTO> files = new ArrayList<>();

        files.addAll(awsTerraformRenderer.render(context.applicationPort()));
        files.addAll(ociTerraformRenderer.render(context.applicationPort()));
        files.add(runtimeDockerfileRenderer.render(context.javaVersion(), context.applicationPort()));
        files.add(cloudComposeRenderer.render(context));
        files.add(cloudDeployWarningRenderer.render());

        return cloudDeployFileAssembler.assemble(files);
    }
}
