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
import com.infragen.infragen.domain.generation.generator.cloud.PostgresCloudComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.RedisCloudComposeServiceRenderer;
import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.PostgreSQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.RedisComponent;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;

@DisplayName("Terraform IaC 생성기")
class TerraformIaCGeneratorTest {
    private static final String TERRAFORM_REQUIRED_VERSION = ">= 1.13.5, < 2.0.0";
    private static final String AWS_PROVIDER_SOURCE = "hashicorp/aws";
    private static final String AWS_PROVIDER_VERSION = "6.22.0";
    private static final String MYSQL_SERVICE_BLOCK = """

          mysql:
            image: mysql:8.4
            env_file:
              - .env
            environment:
              MYSQL_DATABASE: ${MYSQL_DATABASE:?외부 .env에 설정 필요}
              MYSQL_USER: ${MYSQL_USER:?외부 .env에 설정 필요}
              MYSQL_PASSWORD: ${MYSQL_PASSWORD:?외부 .env에 설정 필요}
              MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?외부 .env에 설정 필요}
            volumes:
              - mysql_data:/var/lib/mysql
        """;
    private static final String POSTGRES_SERVICE_BLOCK = """

          postgres:
            image: postgres:17
            env_file:
              - .env
            environment:
              POSTGRES_DB: ${POSTGRES_DB:?외부 .env에 설정 필요}
              POSTGRES_USER: ${POSTGRES_USER:?외부 .env에 설정 필요}
              POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?외부 .env에 설정 필요}
              PGDATA: /var/lib/postgresql/data
            volumes:
              - pg_data:/var/lib/postgresql/data
        """;
    private static final String REDIS_SERVICE_BLOCK = """

          redis:
            image: redis:7.4
            env_file:
              - .env
            command: ["redis-server", "--requirepass", "${REDIS_PASSWORD:?외부 .env에 설정 필요}", "--appendonly", "yes"]
            volumes:
              - redis_data:/data
        """;

    private final TerraformIaCGenerator generator = new TerraformIaCGenerator(
        new CloudComposeRenderer(List.of(
            new MysqlCloudComposeServiceRenderer(),
            new PostgresCloudComposeServiceRenderer(),
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
                .fileName("aws/terraform/main.tf")
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
        assertEquals("cloud/aws/terraform/main.tf", bundle.files().get(0).fileName());
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
            () -> assertTerraformContract(first, "aws/terraform/main.tf", "aws",
                AWS_PROVIDER_SOURCE, AWS_PROVIDER_VERSION),
            () -> assertTrue(fileContent(first, "Dockerfile").contains("FROM eclipse-temurin:17-jre")),
            () -> assertTrue(fileContent(first, "Dockerfile").contains("EXPOSE 9090")),
            () -> assertTrue(fileContent(first, "aws/terraform/main.tf").contains("from_port   = var.app_port")),
            () -> assertTrue(fileContent(first, "aws/terraform/main.tf")
                .contains("Name = var.aws_instance_name")),
            () -> assertTrue(fileContent(first, "aws/terraform/main.tf")
                .contains("cidr_blocks = [var.aws_app_cidr]")),
            () -> assertTrue(fileContent(first, "aws/terraform/variables.tf")
                .contains("variable \"aws_internet_gateway_name\"")),
            () -> assertTrue(fileContent(first, "aws/terraform/terraform.tfvars.example")
                .contains("aws_region = \"ap-northeast-2\"")),
            () -> assertTrue(fileContent(first, "aws/terraform/terraform.tfvars.example")
                .contains("aws_vpc_name = \"infragen-vpc\"")),
            () -> assertTrue(fileContent(first, "aws/terraform/terraform.tfvars.example")
                .contains("aws_ami_id = \"ami-xxxxxxxx\"")),
            () -> assertTrue(fileContent(first, "aws/terraform/variables.tf").contains("default     = 9090")),
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
            () -> assertTrue(fileContent(bundle, "aws/terraform/main.tf").contains("provider \"aws\"")),
            () -> assertFalse(bundle.files().stream()
                .anyMatch(file -> file.fileName().equals("cloud/oci/terraform/main.tf")))
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
            () -> assertTrue(fileContent(bundle, "oci/terraform/main.tf").contains("provider \"oci\"")),
            () -> assertTrue(fileContent(bundle, "oci/terraform/terraform.tfvars.example")
                .contains("oci_compartment_id = \"ocid1.compartment.oc1..aaaa\"")),
            () -> assertFalse(bundle.files().stream()
                .anyMatch(file -> file.fileName().equals("cloud/aws/terraform/main.tf")))
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
        validateTerraformModule(terraformValidationDirectory.resolve("cloud/aws/terraform"));
        validateTerraformModule(terraformValidationDirectory.resolve("cloud/oci/terraform"));

        // then
        assertAll(
            () -> assertTrue(Files.exists(
                terraformValidationDirectory.resolve("cloud/aws/terraform/.terraform.lock.hcl"))),
            () -> assertTrue(Files.exists(
                terraformValidationDirectory.resolve("cloud/oci/terraform/.terraform.lock.hcl")))
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
    @DisplayName("PostgreSQL만 선택 — postgres 서비스, PGDATA 고정, 내부 DNS DataSource 생성")
    void generate_PostgresOnly_RendersServiceAndDataSource() {
        // given
        ParsingResultDTO parsingResult = validParsingResult();
        EdgeDTO edge = new EdgeDTO();
        edge.setSourceNodeId("pg-1");
        edge.setTargetNodeId("node-1");
        parsingResult.setComponents(List.of(parsingResult.getComponents().get(0), postgresComponent()));
        parsingResult.setEdges(List.of(edge));

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult, awsTarget());
        String compose = fileContent(bundle, "docker-compose.cloud.yml");

        // then
        assertAll(
            () -> assertTrue(compose.contains("""

                  postgres:
                    image: postgres:17
                    env_file:
                      - .env
                    environment:
                      POSTGRES_DB: ${POSTGRES_DB:?외부 .env에 설정 필요}
                      POSTGRES_USER: ${POSTGRES_USER:?외부 .env에 설정 필요}
                      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?외부 .env에 설정 필요}
                      PGDATA: /var/lib/postgresql/data
                    volumes:
                      - pg_data:/var/lib/postgresql/data
                """)),
            () -> assertTrue(compose.contains("\nvolumes:\n  pg_data:\n")),
            () -> assertTrue(compose.contains("depends_on:\n      - postgres\n")),
            () -> assertTrue(compose.contains(
                "SPRING_DATASOURCE_URL: \"jdbc:postgresql://postgres:5432/${POSTGRES_DB:?외부 .env에 설정 필요}\"")),
            () -> assertTrue(compose.contains("SPRING_DATASOURCE_USERNAME: \"${POSTGRES_USER:?외부 .env에 설정 필요}\"")),
            () -> assertTrue(compose.contains("POSTGRES_HOST: postgres\n")),
            () -> assertTrue(compose.contains("POSTGRES_PORT: \"5432\"\n")),
            () -> assertFalse(compose.contains("데이터베이스가 2개 이상"))
        );
    }

    @Test
    @DisplayName("MySQL + PostgreSQL — 타입별 접속 변수만 생성하고 SPRING_DATASOURCE_* 대신 안내 주석 추가")
    void generate_MysqlAndPostgres_SkipsDataSourceAndAddsNotice() {
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
        EdgeDTO mysqlEdge = new EdgeDTO();
        mysqlEdge.setSourceNodeId("mysql-1");
        mysqlEdge.setTargetNodeId("node-1");
        EdgeDTO postgresEdge = new EdgeDTO();
        postgresEdge.setSourceNodeId("pg-1");
        postgresEdge.setTargetNodeId("node-1");
        parsingResult.setComponents(List.of(parsingResult.getComponents().get(0), mysql, postgresComponent()));
        parsingResult.setEdges(List.of(mysqlEdge, postgresEdge));

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult, awsTarget());
        String compose = fileContent(bundle, "docker-compose.cloud.yml");

        // then
        assertAll(
            () -> assertTrue(compose.startsWith("""
                # CLOUD_DEPLOY 부트스트랩입니다. 민감한 값은 외부 .env 파일에서 주입해 주세요.
                # 애플리케이션에 데이터베이스가 2개 이상 연결되어 SPRING_DATASOURCE_*를 생성하지 않았습니다.
                # DataSource를 직접 설정하고 타입별 접속 변수(MYSQL_*, POSTGRES_*)를 사용하세요.
                services:
                """)),
            () -> assertTrue(compose.contains("\n  mysql:\n")),
            () -> assertTrue(compose.contains("\n  postgres:\n")),
            () -> assertTrue(compose.contains("MYSQL_HOST: mysql\n")),
            () -> assertTrue(compose.contains("POSTGRES_HOST: postgres\n")),
            () -> assertTrue(compose.contains("      - mysql\n")),
            () -> assertTrue(compose.contains("      - postgres\n")),
            () -> assertFalse(compose.contains("SPRING_DATASOURCE_URL:")),
            () -> assertFalse(compose.contains("SPRING_DATASOURCE_USERNAME:")),
            () -> assertFalse(compose.contains("SPRING_DATASOURCE_PASSWORD:"))
        );
    }

    @Test
    @DisplayName("MySQL만 선택 — Cloud Compose 전체 출력이 기준과 같다")
    void generate_MysqlOnly_RendersExactCloudCompose() {
        // given
        ParsingResultDTO parsingResult = validParsingResult();
        parsingResult.setComponents(List.of(parsingResult.getComponents().get(0), mysqlComponent()));
        parsingResult.setEdges(List.of(edgeToApplication("mysql-1")));

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult, awsTarget());

        // then
        assertEquals("""
            # CLOUD_DEPLOY 부트스트랩입니다. 민감한 값은 외부 .env 파일에서 주입해 주세요.
            services:
              app:
                build:
                  context: .
                  dockerfile: Dockerfile
                image: infragen-runtime:plan-only
                ports:
                  - "${APP_PORT:-9090}:9090"
                environment:
                  SPRING_DATASOURCE_URL: "jdbc:mysql://mysql:3306/${MYSQL_DATABASE:?외부 .env에 설정 필요}"
                  SPRING_DATASOURCE_USERNAME: "${MYSQL_USER:?외부 .env에 설정 필요}"
                  SPRING_DATASOURCE_PASSWORD: "${MYSQL_PASSWORD:?외부 .env에 설정 필요}"
                  MYSQL_HOST: mysql
                  MYSQL_PORT: "3306"
                env_file:
                  - .env
                depends_on:
                  - mysql
            """ + MYSQL_SERVICE_BLOCK + """

            volumes:
              mysql_data:
            """, fileContent(bundle, "docker-compose.cloud.yml"));
    }

    @Test
    @DisplayName("PostgreSQL만 선택 — Cloud Compose 전체 출력이 기준과 같다")
    void generate_PostgresOnly_RendersExactCloudCompose() {
        // given
        ParsingResultDTO parsingResult = validParsingResult();
        parsingResult.setComponents(List.of(parsingResult.getComponents().get(0), postgresComponent()));
        parsingResult.setEdges(List.of(edgeToApplication("pg-1")));

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult, awsTarget());

        // then
        assertEquals("""
            # CLOUD_DEPLOY 부트스트랩입니다. 민감한 값은 외부 .env 파일에서 주입해 주세요.
            services:
              app:
                build:
                  context: .
                  dockerfile: Dockerfile
                image: infragen-runtime:plan-only
                ports:
                  - "${APP_PORT:-9090}:9090"
                environment:
                  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres:5432/${POSTGRES_DB:?외부 .env에 설정 필요}"
                  SPRING_DATASOURCE_USERNAME: "${POSTGRES_USER:?외부 .env에 설정 필요}"
                  SPRING_DATASOURCE_PASSWORD: "${POSTGRES_PASSWORD:?외부 .env에 설정 필요}"
                  POSTGRES_HOST: postgres
                  POSTGRES_PORT: "5432"
                env_file:
                  - .env
                depends_on:
                  - postgres
            """ + POSTGRES_SERVICE_BLOCK + """

            volumes:
              pg_data:
            """, fileContent(bundle, "docker-compose.cloud.yml"));
    }

    @Test
    @DisplayName("MySQL + Redis 선택 — Cloud Compose 전체 출력이 기준과 같다")
    void generate_MysqlAndRedis_RendersExactCloudCompose() {
        // given
        ParsingResultDTO parsingResult = validParsingResult();
        parsingResult.setComponents(List.of(
            parsingResult.getComponents().get(0), mysqlComponent(), redisComponent()));
        parsingResult.setEdges(List.of(edgeToApplication("mysql-1"), edgeToApplication("redis-1")));

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult, awsTarget());

        // then
        assertEquals("""
            # CLOUD_DEPLOY 부트스트랩입니다. 민감한 값은 외부 .env 파일에서 주입해 주세요.
            services:
              app:
                build:
                  context: .
                  dockerfile: Dockerfile
                image: infragen-runtime:plan-only
                ports:
                  - "${APP_PORT:-9090}:9090"
                environment:
                  SPRING_DATASOURCE_URL: "jdbc:mysql://mysql:3306/${MYSQL_DATABASE:?외부 .env에 설정 필요}"
                  SPRING_DATASOURCE_USERNAME: "${MYSQL_USER:?외부 .env에 설정 필요}"
                  SPRING_DATASOURCE_PASSWORD: "${MYSQL_PASSWORD:?외부 .env에 설정 필요}"
                  MYSQL_HOST: mysql
                  MYSQL_PORT: "3306"
                  REDIS_HOST: redis
                  REDIS_PORT: "6379"
                  REDIS_PASSWORD: "${REDIS_PASSWORD:?외부 .env에 설정 필요}"
                env_file:
                  - .env
                depends_on:
                  - mysql
                  - redis
            """ + MYSQL_SERVICE_BLOCK + REDIS_SERVICE_BLOCK + """

            volumes:
              mysql_data:
              redis_data:
            """, fileContent(bundle, "docker-compose.cloud.yml"));
    }

    @Test
    @DisplayName("MySQL + PostgreSQL 선택 — Cloud Compose 전체 출력이 기준과 같다")
    void generate_MysqlAndPostgres_RendersExactCloudCompose() {
        // given
        ParsingResultDTO parsingResult = validParsingResult();
        parsingResult.setComponents(List.of(
            parsingResult.getComponents().get(0), mysqlComponent(), postgresComponent()));
        parsingResult.setEdges(List.of(edgeToApplication("mysql-1"), edgeToApplication("pg-1")));

        // when
        IaCFileDTO.BundleResDTO bundle = generator.generate(parsingResult, awsTarget());

        // then
        assertEquals("""
            # CLOUD_DEPLOY 부트스트랩입니다. 민감한 값은 외부 .env 파일에서 주입해 주세요.
            # 애플리케이션에 데이터베이스가 2개 이상 연결되어 SPRING_DATASOURCE_*를 생성하지 않았습니다.
            # DataSource를 직접 설정하고 타입별 접속 변수(MYSQL_*, POSTGRES_*)를 사용하세요.
            services:
              app:
                build:
                  context: .
                  dockerfile: Dockerfile
                image: infragen-runtime:plan-only
                ports:
                  - "${APP_PORT:-9090}:9090"
                environment:
                  MYSQL_HOST: mysql
                  MYSQL_PORT: "3306"
                  POSTGRES_HOST: postgres
                  POSTGRES_PORT: "5432"
                env_file:
                  - .env
                depends_on:
                  - mysql
                  - postgres
            """ + MYSQL_SERVICE_BLOCK + POSTGRES_SERVICE_BLOCK + """

            volumes:
              mysql_data:
              pg_data:
            """, fileContent(bundle, "docker-compose.cloud.yml"));
    }

    @Test
    @DisplayName("동일한 연결 MySQL 중복 — AMBIGUOUS_DEPENDENCY_CONFIGURATION")
    void generate_DuplicateConnectedMysql_ThrowsAmbiguousDependencyConfiguration() {
        // given
        ParsingResultDTO parsingResult = validParsingResult();
        MySQLComponent firstMysql = MySQLComponent.builder()
            .id("mysql-1")
            .posX(0f)
            .posY(0f)
            .imageVersion("mysql:8.4")
            .containerName("mysql-primary")
            .port(3306)
            .volumeName("mysql_primary_data")
            .build();
        MySQLComponent secondMysql = MySQLComponent.builder()
            .id("mysql-2")
            .posX(0f)
            .posY(100f)
            .imageVersion("mysql:8.4")
            .containerName("mysql-secondary")
            .port(3307)
            .volumeName("mysql_secondary_data")
            .build();
        EdgeDTO firstEdge = new EdgeDTO();
        firstEdge.setSourceNodeId("mysql-1");
        firstEdge.setTargetNodeId("node-1");
        EdgeDTO secondEdge = new EdgeDTO();
        secondEdge.setSourceNodeId("mysql-2");
        secondEdge.setTargetNodeId("node-1");
        parsingResult.setComponents(List.of(parsingResult.getComponents().get(0), firstMysql, secondMysql));
        parsingResult.setEdges(List.of(firstEdge, secondEdge));

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> generator.generate(parsingResult, awsTarget())
        );

        // then
        assertEquals(
            IaCGenerationErrorCode.AMBIGUOUS_DEPENDENCY_CONFIGURATION,
            exception.getCode()
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

    private static MySQLComponent mysqlComponent() {
        return MySQLComponent.builder()
            .id("mysql-1")
            .posX(0f)
            .posY(0f)
            .imageVersion("mysql:8.4")
            .containerName("mysql")
            .port(3306)
            .volumeName("mysql_data")
            .build();
    }

    private static RedisComponent redisComponent() {
        return RedisComponent.builder()
            .id("redis-1")
            .posX(0f)
            .posY(100f)
            .imageVersion("redis:7.4")
            .containerName("redis")
            .port(6379)
            .volumeName("redis_data")
            .password("redis-password")
            .build();
    }

    private static EdgeDTO edgeToApplication(String sourceNodeId) {
        EdgeDTO edge = new EdgeDTO();
        edge.setSourceNodeId(sourceNodeId);
        edge.setTargetNodeId("node-1");
        return edge;
    }

    private static PostgreSQLComponent postgresComponent() {
        return PostgreSQLComponent.builder()
            .id("pg-1")
            .posX(0f)
            .posY(200f)
            .imageVersion("postgres:17")
            .containerName("postgres")
            .port(5432)
            .volumeName("pg_data")
            .build();
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
            if (!file.fileName().startsWith("cloud/aws/terraform/")
                && !file.fileName().startsWith("cloud/oci/terraform/")) {
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
        useProviderCache(processBuilder);
        Process process = processBuilder.start();
        boolean completed = process.waitFor(3, TimeUnit.MINUTES);

        if (!completed) {
            process.destroyForcibly();
            throw new AssertionError("Terraform command timed out: " + String.join(" ", arguments));
        }

        String output = Files.readString(logFile);
        assertEquals(0, process.exitValue(), output);
    }

    // init이 실행마다 aws·oci provider를 새로 내려받아 이 테스트가 4분 가까이 걸리므로 provider를 캐시 디렉터리에 재사용한다.
    // 이미 지정된 TF_PLUGIN_CACHE_DIR(CI 등)은 그대로 쓰고, 없으면 사용자 기본 위치를 만든다. terraform은 이 디렉터리가 없으면 캐시를 쓰지 않는다.
    // 검증마다 새 임시 디렉터리를 쓰므로 .terraform.lock.hcl이 없다. terraform은 잠금 파일에 체크섬이 없으면 캐시를 무시하고 다시 내려받으므로,
    // 버려지는 임시 모듈에서는 잠금 파일 검증을 건너뛰도록 허용한다.
    private static void useProviderCache(ProcessBuilder processBuilder) throws IOException {
        processBuilder.environment().put("TF_PLUGIN_CACHE_MAY_BREAK_DEPENDENCY_LOCK_FILE", "true");
        if (System.getenv("TF_PLUGIN_CACHE_DIR") != null) {
            return;
        }
        Path cacheDirectory = Path.of(System.getProperty("user.home"), ".terraform.d", "plugin-cache");
        Files.createDirectories(cacheDirectory);
        processBuilder.environment().put("TF_PLUGIN_CACHE_DIR", cacheDirectory.toString());
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
