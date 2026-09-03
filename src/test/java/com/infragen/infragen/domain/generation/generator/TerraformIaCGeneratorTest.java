package com.infragen.infragen.domain.generation.generator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.infragen.infragen.domain.generation.dto.request.DeploymentTargetReqDTO;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.generation.generator.cloud.CloudDeployFileAssembler;
import com.infragen.infragen.domain.generation.generator.cloud.CloudComposeRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.AwsTerraformRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.MysqlCloudComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.OciTerraformRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.RedisCloudComposeServiceRenderer;
import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.RedisComponent;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;

@DisplayName("Terraform IaC 생성기")
class TerraformIaCGeneratorTest {
    private static final String TERRAFORM_REQUIRED_VERSION = ">= 1.13.5, < 2.0.0";
    private static final String AWS_PROVIDER_SOURCE = "hashicorp/aws";
    private static final String AWS_PROVIDER_VERSION = "6.22.0";

    private final TerraformIaCGenerator generator = new TerraformIaCGenerator(
        new CloudComposeRenderer(List.of(
            new MysqlCloudComposeServiceRenderer(),
            new RedisCloudComposeServiceRenderer()
        )),
        List.of(new AwsTerraformRenderer(), new OciTerraformRenderer())
    );
    private final CloudDeployFileAssembler fileAssembler = new CloudDeployFileAssembler();

    @TempDir
    Path terraformValidationDirectory;

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
        assertEquals("cloud/terraform/aws/main.tf", bundle.files().get(0).fileName());
        assertEquals("cloud/Dockerfile", bundle.files().get(1).fileName());
        assertEquals(files.get(0).content(), bundle.files().get(0).content());
        assertEquals(files.get(1).content(), bundle.files().get(1).content());
    }

    @Test
    @DisplayName("AWS target — scaffold와 runtime 산출물을 deterministic하게 생성")
    void generate_AwsTarget_ReturnsDeterministicScaffold() {
        // given
        ParsingResultDTO parsingResult = validParsingResult();

        // when
        IaCFileDTO.BundleResDTO first = generator.generate(parsingResult, awsTarget());
        IaCFileDTO.BundleResDTO second = generator.generate(parsingResult, awsTarget());

        // then
        assertAll(
            () -> assertEquals(6, first.files().size()),
            () -> assertEquals(first.files(), second.files()),
            () -> assertTerraformContract(first, "terraform/aws/main.tf", "aws",
                AWS_PROVIDER_SOURCE, AWS_PROVIDER_VERSION),
            () -> assertTrue(fileContent(first, "Dockerfile").contains("FROM eclipse-temurin:17-jre")),
            () -> assertTrue(fileContent(first, "Dockerfile").contains("EXPOSE 9090")),
            () -> assertTrue(fileContent(first, "terraform/aws/main.tf").contains("from_port   = var.app_port")),
            () -> assertTrue(fileContent(first, "terraform/aws/main.tf")
                .contains("Name = var.aws_instance_name")),
            () -> assertTrue(fileContent(first, "terraform/aws/main.tf")
                .contains("cidr_blocks = [var.aws_app_cidr]")),
            () -> assertTrue(fileContent(first, "terraform/aws/variables.tf")
                .contains("variable \"aws_internet_gateway_name\"")),
            () -> assertTrue(fileContent(first, "terraform/aws/terraform.tfvars.example")
                .contains("aws_region = \"ap-northeast-2\"")),
            () -> assertTrue(fileContent(first, "terraform/aws/terraform.tfvars.example")
                .contains("aws_vpc_name = \"infragen-vpc\"")),
            () -> assertTrue(fileContent(first, "terraform/aws/terraform.tfvars.example")
                .contains("aws_ami_id = \"ami-xxxxxxxx\"")),
            () -> assertTrue(fileContent(first, "terraform/aws/variables.tf").contains("default     = 9090")),
            () -> assertTrue(fileContent(first, "docker-compose.cloud.yml")
                .contains("${APP_PORT:-9090}:9090")),
            () -> assertFalse(fileContent(first, "docker-compose.cloud.yml").contains("mysql:")),
            () -> assertFalse(fileContent(first, "docker-compose.cloud.yml").contains("redis:")),
            () -> assertFalse(fileContent(first, "docker-compose.cloud.yml").contains("depends_on:")),
            () -> assertEquals("cloud/Dockerfile", first.files().get(3).fileName()),
            () -> assertEquals("cloud/docker-compose.cloud.yml", first.files().get(4).fileName()),
            () -> assertEquals("cloud/CLOUD_DEPLOY_WARNING.md", first.files().get(5).fileName()),
            () -> assertTrueContains(first, "apply_ready=false"),
            () -> assertTrueContains(first, "aws_instance"),
            () -> assertTrueContains(first, "COPY app.jar app.jar"),
            () -> assertFalse(allContent(first).contains("userpass12")),
            () -> assertFalse(allContent(first).contains("rootpass12")),
            () -> assertFalseContainsAny(first,
                "DB_PASSWORD", "JWT_SECRET", "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY",
                "OCI_TENANCY_OCID", "OCI_USER_OCID", "OCI_FINGERPRINT", "OCI_PRIVATE_KEY"),
            () -> assertFalse(allContent(first).contains("eclipse-temurin:21-jre")),
            () -> assertTrue(allContent(first).contains("infragen-vpc")),
            () -> assertTrue(allContent(first).contains("infragen-igw"))
        );
    }

    @Test
    @DisplayName("AWS target 선택 — AWS Terraform만 생성")
    void generate_AwsTarget_RendersOnlyAwsTerraform() {
        // given
        DeploymentTargetReqDTO.Target target = awsTarget();

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(validParsingResult(), target);

        // then
        assertAll(
            () -> assertTrue(fileContent(bundle, "terraform/aws/main.tf").contains("provider \"aws\"")),
            () -> assertFalse(bundle.files().stream()
                .anyMatch(file -> file.fileName().equals("terraform/oci/main.tf")))
        );
    }

    @Test
    @DisplayName("OCI target 선택 — OCI Terraform만 생성")
    void generate_OciTarget_RendersOnlyOciTerraform() {
        // given
        DeploymentTargetReqDTO.Target target = ociTarget();

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(validParsingResult(), target);

        // then
        assertAll(
            () -> assertTrue(fileContent(bundle, "terraform/oci/main.tf").contains("provider \"oci\"")),
            () -> assertTrue(fileContent(bundle, "terraform/oci/terraform.tfvars.example")
                .contains("oci_compartment_id = \"ocid1.compartment.oc1..aaaa\"")),
            () -> assertFalse(bundle.files().stream()
                .anyMatch(file -> file.fileName().equals("terraform/aws/main.tf")))
        );
    }

    @Test
    @DisplayName("target 누락 — GENERATION400_2")
    void generate_NullTarget_ThrowsGenerationException() {
        // given
        ParsingResultDTO parsingResult = validParsingResult();

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> generator.generate(parsingResult, null)
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
    }

    @Test
    @DisplayName("CLOUD_DEPLOY — AWS·OCI Terraform 산출물이 CLI 정적 검증을 통과")
    void generate_CloudDeploy_TerraformFilesPassCliValidation() throws Exception {
        // given
        IaCFileDTO.BundleResDTO awsBundle = generator.generate(validParsingResult(), awsTarget());
        IaCFileDTO.BundleResDTO ociBundle = generator.generate(validParsingResult(), ociTarget());
        writeTerraformFiles(awsBundle);
        writeTerraformFiles(ociBundle);

        // when
        validateTerraformModule(terraformValidationDirectory.resolve("cloud/terraform/aws"));
        validateTerraformModule(terraformValidationDirectory.resolve("cloud/terraform/oci"));

        // then
        assertAll(
            () -> assertTrue(Files.exists(
                terraformValidationDirectory.resolve("cloud/terraform/aws/.terraform.lock.hcl"))),
            () -> assertTrue(Files.exists(
                terraformValidationDirectory.resolve("cloud/terraform/oci/.terraform.lock.hcl")))
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
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult, awsTarget());
        String compose = fileContent(bundle, "docker-compose.cloud.yml");

        // then
        assertAll(
            () -> assertTrue(compose.contains("mysql:")),
            () -> assertTrue(compose.contains("image: mysql:8.4")),
            () -> assertFalse(compose.contains("image: mysql:8.0")),
            () -> assertTrue(compose.contains("MYSQL_DATABASE")),
            () -> assertTrue(compose.contains("      - mysql_data:/var/lib/mysql")),
            () -> assertTrue(compose.contains("\nvolumes:\n  mysql_data:\n")),
            () -> assertTrue(compose.contains("depends_on:")),
            () -> assertTrue(compose.contains("      - mysql")),
            () -> assertFalse(compose.contains("redis:")),
            () -> assertFalse(compose.contains("REDIS_PASSWORD"))
        );
    }

    @Test
    @DisplayName("MySQL + Redis 선택 — 내부 DNS 환경변수와 Redis Compose 생성")
    void generate_MysqlAndRedis_RendersInternalDnsAndRedisService() {
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
        RedisComponent redis = RedisComponent.builder()
            .id("redis-1")
            .posX(0f)
            .posY(100f)
            .imageVersion("redis:7.4")
            .containerName("redis")
            .port(6379)
            .volumeName("redis_data")
            .password("redis-password")
            .build();
        EdgeDTO mysqlEdge = new EdgeDTO();
        mysqlEdge.setSourceNodeId("mysql-1");
        mysqlEdge.setTargetNodeId("node-1");
        EdgeDTO redisEdge = new EdgeDTO();
        redisEdge.setSourceNodeId("redis-1");
        redisEdge.setTargetNodeId("node-1");
        parsingResult.setComponents(List.of(parsingResult.getComponents().get(0), mysql, redis));
        parsingResult.setEdges(List.of(mysqlEdge, redisEdge));

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult, awsTarget());
        String compose = fileContent(bundle, "docker-compose.cloud.yml");

        // then
        assertAll(
            () -> assertTrue(compose.contains("redis:")),
            () -> assertTrue(compose.contains("image: redis:7.4")),
            () -> assertTrue(compose.contains("redis_data:/data")),
            () -> assertTrue(compose.contains("REDIS_HOST: redis")),
            () -> assertTrue(compose.contains("REDIS_PORT: \"6379\"")),
            () -> assertTrue(compose.contains("REDIS_PASSWORD: \"${REDIS_PASSWORD:?외부 .env에 설정 필요}\"")),
            () -> assertTrue(compose.contains("SPRING_DATASOURCE_URL: \"jdbc:mysql://mysql:3306/")),
            () -> assertTrue(compose.contains("MYSQL_HOST: mysql")),
            () -> assertTrue(compose.contains("      - mysql\n")),
            () -> assertTrue(compose.contains("      - redis\n")),
            () -> assertTrue(compose.contains("  mysql_data:\n")),
            () -> assertTrue(compose.contains("  redis_data:\n"))
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
            () -> generator.generate(parsingResult, awsTarget())
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
    }

    private static ParsingResultDTO validParsingResult() {
        return validParsingResult("17", 9090);
    }

    private static DeploymentTargetReqDTO.Target awsTarget() {
        return new DeploymentTargetReqDTO.AwsDeploymentTarget(
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
    }

    private static DeploymentTargetReqDTO.Target ociTarget() {
        return new DeploymentTargetReqDTO.OciDeploymentTarget(
            "ap-seoul-1",
            "infragen-vcn",
            "infragen-subnet",
            "infragen-igw",
            "infragen-public-route",
            "infragen-security-list",
            "infragen-app",
            "infragen-app-host",
            "ocid1.compartment.oc1..aaaa",
            "AD-1",
            "ocid1.image.oc1..aaaa",
            "VM.Standard.E2.1.Micro",
            "10.0.0.0/16",
            "10.0.1.0/24",
            "203.0.113.10/32",
            "0.0.0.0/0",
            "ssh-rsa AAAAexample infragen"
        );
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
        String scopedFileName = fileName.startsWith("cloud/")
            ? fileName
            : "cloud/" + fileName;
        return bundle.files().stream()
            .filter(file -> scopedFileName.equals(file.fileName()))
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

    private static void assertTerraformContract(
        IaCFileDTO.BundleResDTO bundle,
        String fileName,
        String providerName,
        String providerSource,
        String providerVersion
    ) {
        String content = fileContent(bundle, fileName);
        assertAll(
            () -> assertTrue(content.contains("required_version = \"" + TERRAFORM_REQUIRED_VERSION + "\"")),
            () -> assertTrue(content.contains(providerName + " = {")),
            () -> assertTrue(content.contains("source  = \"" + providerSource + "\"")),
            () -> assertTrue(content.contains("version = \"" + providerVersion + "\""))
        );
    }

    private void writeTerraformFiles(IaCFileDTO.BundleResDTO bundle) throws IOException {
        for (IaCFileDTO.FileContentResDTO file : bundle.files()) {
            if (!file.fileName().startsWith("cloud/terraform/")) {
                continue;
            }

            Path target = terraformValidationDirectory.resolve(file.fileName());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.content());
        }
    }

    private static void validateTerraformModule(Path moduleDirectory) throws IOException, InterruptedException {
        runTerraform(moduleDirectory, "init", "-backend=false", "-input=false", "-no-color");
        runTerraform(moduleDirectory, "fmt", "-check", "-no-color");
        runTerraform(moduleDirectory, "validate", "-no-color");
    }

    private static void runTerraform(Path moduleDirectory, String... arguments)
        throws IOException, InterruptedException {
        Path logFile = Files.createTempFile(moduleDirectory, "terraform-", ".log");
        ProcessBuilder processBuilder = new ProcessBuilder(buildTerraformCommand(arguments))
            .directory(moduleDirectory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(logFile.toFile());
        Process process = processBuilder.start();
        boolean completed = process.waitFor(3, TimeUnit.MINUTES);

        if (!completed) {
            process.destroyForcibly();
            throw new AssertionError("Terraform command timed out: " + String.join(" ", arguments));
        }

        String output = Files.readString(logFile);
        assertEquals(0, process.exitValue(), output);
    }

    private static List<String> buildTerraformCommand(String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("terraform");
        command.addAll(List.of(arguments));
        return command;
    }

    private static void assertFalseContainsAny(IaCFileDTO.BundleResDTO bundle, String... forbiddenValues) {
        String content = allContent(bundle);
        for (String forbiddenValue : forbiddenValues) {
            assertFalse(content.contains(forbiddenValue), "금지된 값이 산출물에 포함됨: " + forbiddenValue);
        }
    }
}
