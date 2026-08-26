package com.infragen.infragen.domain.generation.generator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.generation.generator.cloud.CloudDeployFileAssembler;
import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;

@DisplayName("Terraform IaC 생성기")
class TerraformIaCGeneratorTest {
    private final TerraformIaCGenerator generator = new TerraformIaCGenerator();
    private final CloudDeployFileAssembler fileAssembler = new CloudDeployFileAssembler();

    @Test
    @DisplayName("OutputFormat — TERRAFORM")
    void getOutputFormat_ReturnsTerraform() {
        assertEquals(OutputFormat.TERRAFORM, generator.getOutputFormat());
    }

    @Test
    @DisplayName("renderer 파일 순서 유지 — bundle 조립")
    void assemble_PreservesRendererFileOrder() {
        // given
        List<IaCFileDTO.FileContentResDTO> files = List.of(
            IaCFileDTO.FileContentResDTO.builder()
                .fileName("terraform/aws/main.tf")
                .content("aws")
                .build(),
            IaCFileDTO.FileContentResDTO.builder()
                .fileName("Dockerfile")
                .content("docker")
                .build()
        );

        // when
        IaCFileDTO.BundleResDTO bundle = fileAssembler.assemble(files);

        // then
        assertEquals(files, bundle.files());
    }

    @Test
    @DisplayName("CLOUD_DEPLOY — AWS·OCI scaffold와 runtime 산출물을 deterministic하게 생성")
    void generate_CloudDeploy_ReturnsDeterministicScaffold() {
        // given
        ParsingResultDTO parsingResult = validParsingResult();

        // when
        IaCFileDTO.BundleResDTO first = generator.generate(parsingResult);
        IaCFileDTO.BundleResDTO second = generator.generate(parsingResult);

        // then
        assertAll(
            () -> assertEquals(7, first.files().size()),
            () -> assertEquals(first.files(), second.files()),
            () -> assertTrue(fileContent(first, "terraform/aws/main.tf").contains("version = \"6.22.0\"")),
            () -> assertTrue(fileContent(first, "terraform/oci/main.tf").contains("version = \"8.8.0\"")),
            () -> assertTrue(fileContent(first, "Dockerfile").contains("FROM eclipse-temurin:17-jre")),
            () -> assertTrue(fileContent(first, "Dockerfile").contains("EXPOSE 9090")),
            () -> assertTrue(fileContent(first, "terraform/aws/main.tf").contains("from_port   = var.app_port")),
            () -> assertTrue(fileContent(first, "terraform/oci/main.tf").contains("min = var.app_port")),
            () -> assertTrue(fileContent(first, "terraform/aws/main.tf")
                .contains("Name = var.aws_instance_name")),
            () -> assertTrue(fileContent(first, "terraform/aws/main.tf")
                .contains("cidr_blocks = [var.aws_app_cidr]")),
            () -> assertTrue(fileContent(first, "terraform/aws/variables.tf")
                .contains("variable \"aws_internet_gateway_name\"")),
            () -> assertTrue(fileContent(first, "terraform/oci/main.tf")
                .contains("display_name = var.oci_instance_name")),
            () -> assertTrue(fileContent(first, "terraform/oci/main.tf")
                .contains("hostname_label   = var.oci_hostname_label")),
            () -> assertTrue(fileContent(first, "terraform/oci/main.tf")
                .contains("source   = var.oci_app_cidr")),
            () -> assertTrue(fileContent(first, "terraform/oci/variables.tf")
                .contains("variable \"oci_internet_gateway_name\"")),
            () -> assertTrue(fileContent(first, "terraform/aws/variables.tf").contains("default     = 9090")),
            () -> assertTrue(fileContent(first, "docker-compose.cloud.yml")
                .contains("${APP_PORT:-9090}:9090")),
            () -> assertFalse(fileContent(first, "docker-compose.cloud.yml").contains("mysql:")),
            () -> assertFalse(fileContent(first, "docker-compose.cloud.yml").contains("redis:")),
            () -> assertFalse(fileContent(first, "docker-compose.cloud.yml").contains("depends_on:")),
            () -> assertEquals("Dockerfile", first.files().get(4).fileName()),
            () -> assertEquals("docker-compose.cloud.yml", first.files().get(5).fileName()),
            () -> assertEquals("CLOUD_DEPLOY_WARNING.md", first.files().get(6).fileName()),
            () -> assertTrueContains(first, "apply_ready=false"),
            () -> assertTrueContains(first, "aws_instance"),
            () -> assertTrueContains(first, "oci_core_instance"),
            () -> assertTrueContains(first, "COPY app.jar app.jar"),
            () -> assertFalse(allContent(first).contains("userpass12")),
            () -> assertFalse(allContent(first).contains("rootpass12")),
            () -> assertFalse(allContent(first).contains("eclipse-temurin:21-jre")),
            () -> assertFalse(allContent(first).contains("infragen-vpc")),
            () -> assertFalse(allContent(first).contains("infragen-igw"))
        );
    }

    @Test
    @DisplayName("MySQL만 선택 — MySQL과 연결된 depends_on만 생성")
    void generate_MysqlOnly_RendersSelectedServiceAndDependency() {
        // given
        ParsingResultDTO parsingResult = validParsingResult();
        MySQLComponent mysql = MySQLComponent.builder()
            .id("mysql-1")
            .posX(0f)
            .posY(0f)
            .imageVersion("mysql:8.4")
            .containerName("mysql")
            .port(3306)
            .volumeName("mysql_data")
            .build();
        EdgeDTO edge = new EdgeDTO();
        edge.setSourceNodeId("mysql-1");
        edge.setTargetNodeId("node-1");
        parsingResult.setComponents(List.of(parsingResult.getComponents().get(0), mysql));
        parsingResult.setEdges(List.of(edge));

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult);
        String compose = fileContent(bundle, "docker-compose.cloud.yml");

        // then
        assertAll(
            () -> assertTrue(compose.contains("mysql:")),
            () -> assertTrue(compose.contains("image: mysql:8.4")),
            () -> assertFalse(compose.contains("image: mysql:8.0")),
            () -> assertTrue(compose.contains("MYSQL_DATABASE")),
            () -> assertTrue(compose.contains("depends_on:")),
            () -> assertTrue(compose.contains("      - mysql")),
            () -> assertFalse(compose.contains("redis:")),
            () -> assertFalse(compose.contains("REDIS_PASSWORD"))
        );
    }

    @Test
    @DisplayName("Spring Boot 컴포넌트 누락 — GENERATION400_2")
    void generate_ApplicationComponentMissing_ThrowsGenerationException() {
        // given
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        parsingResult.setProjectId(1L);
        parsingResult.setComponents(List.of());

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> generator.generate(parsingResult)
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
    }

    private static ParsingResultDTO validParsingResult() {
        return validParsingResult("17", 9090);
    }

    private static ParsingResultDTO validParsingResult(String javaVersion, int port) {
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        parsingResult.setProjectId(1L);
        parsingResult.setComponents(List.of(SpringBootComponent.builder()
            .id("node-1")
            .posX(100f)
            .posY(100f)
            .name("app")
            .port(port)
            .javaVersion(javaVersion)
            .containerName("app")
            .build()));
        return parsingResult;
    }

    private static String fileContent(IaCFileDTO.BundleResDTO bundle, String fileName) {
        return bundle.files().stream()
            .filter(file -> fileName.equals(file.fileName()))
            .findFirst()
            .orElseThrow()
            .content();
    }

    private static String allContent(IaCFileDTO.BundleResDTO bundle) {
        return bundle.files().stream()
            .map(IaCFileDTO.FileContentResDTO::content)
            .reduce("", String::concat);
    }

    private static void assertTrueContains(IaCFileDTO.BundleResDTO bundle, String expected) {
        assertTrue(allContent(bundle).contains(expected));
    }
}
