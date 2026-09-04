package com.infragen.infragen.domain.generation.generator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.ContextConfiguration;
import org.junit.jupiter.api.extension.ExtendWith;

import com.infragen.infragen.domain.generation.dto.request.DeploymentTargetReqDTO;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.generator.cloud.AwsTerraformRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.CloudComposeRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.OciTerraformRenderer;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    TerraformIaCGenerator.class,
    CloudComposeRenderer.class,
    AwsTerraformRenderer.class,
    OciTerraformRenderer.class
})
@DisplayName("Terraform IaC generator Spring context 테스트")
class TerraformIaCGeneratorContextTest {

    @Autowired
    private TerraformIaCGenerator generator;

    @Test
    @DisplayName("AWS와 OCI renderer가 context에서 주입되어 AWS target을 선택")
    void generate_AwsTarget_UsesSpringRegisteredRenderer() {
        // given
        ParsingResultDTO parsingResult = validParsingResult();
        DeploymentTargetReqDTO.Target target = new DeploymentTargetReqDTO.AwsDeploymentTarget(
            "ap-northeast-2",
            "infragen-vpc",
            "infragen-subnet",
            "infragen-igw",
            "infragen-public-route",
            "infragen-sg",
            "infragen-app",
            "10.0.0.0/16",
            "10.0.1.0/24",
            "ami-xxxxxxxx",
            "t3.micro",
            "203.0.113.10/32",
            "0.0.0.0/0"
        );

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult, target);

        // then
        assertTrue(fileNames(bundle).contains("cloud/aws/terraform/main.tf"));
        assertFalse(fileNames(bundle).contains("cloud/oci/terraform/main.tf"));
    }

    private static ParsingResultDTO validParsingResult() {
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        parsingResult.setProjectId(1L);
        parsingResult.setComponents(List.of(SpringBootComponent.builder()
            .id("node-1")
            .posX(100f)
            .posY(100f)
            .name("app")
            .port(8080)
            .javaVersion("17")
            .containerName("app")
            .build()));
        return parsingResult;
    }

    private static List<String> fileNames(IaCFileDTO.BundleResDTO bundle) {
        return bundle.files().stream()
            .map(IaCFileDTO.FileContentResDTO::fileName)
            .toList();
    }
}
