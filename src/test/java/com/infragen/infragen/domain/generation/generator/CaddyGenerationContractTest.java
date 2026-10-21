package com.infragen.infragen.domain.generation.generator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.generation.dto.request.DeploymentTargetReqDTO;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.generator.application.NodeJsApplicationEnvMapper;
import com.infragen.infragen.domain.generation.generator.application.SpringBootApplicationEnvMapper;
import com.infragen.infragen.domain.generation.generator.cloud.AwsTerraformRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.CaddyCloudComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.CloudComposeRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.MysqlCloudComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.NodeJsRuntimeDockerfileRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.OciTerraformRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.SpringBootRuntimeDockerfileRenderer;
import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.parser.CaddyParser;
import com.infragen.infragen.domain.parsing.parser.MySQLParser;
import com.infragen.infragen.domain.parsing.parser.NodeJsParser;
import com.infragen.infragen.domain.parsing.parser.SpringBootParser;
import com.infragen.infragen.domain.parsing.service.ParsingService;
import com.infragen.infragen.domain.parsing.validator.ValidateGraphStructure;

import tools.jackson.databind.ObjectMapper;

/**
 * 원본 그래프의 Caddy 노드가 parsing부터 CLOUD_DEPLOY 생성까지 이어지는지 확인한다.
 * 개별 산출물의 정확한 내용은 각 renderer와 generator 테스트가 맡는다.
 */
@DisplayName("Caddy 그래프 Generate 계약")
class CaddyGenerationContractTest {

    private ParsingService parsingService;
    private TerraformIaCGenerator cloudGenerator;

    @BeforeEach
    void setUp() {
        parsingService = new ParsingService(
            new ObjectMapper(),
            new ValidateGraphStructure(),
            List.of(new SpringBootParser(), new NodeJsParser(), new MySQLParser(), new CaddyParser())
        );
        cloudGenerator = new TerraformIaCGenerator(
            new CloudComposeRenderer(
                List.of(new MysqlCloudComposeServiceRenderer(), new CaddyCloudComposeServiceRenderer()),
                List.of(new SpringBootApplicationEnvMapper(), new NodeJsApplicationEnvMapper())
            ),
            List.of(new SpringBootRuntimeDockerfileRenderer(), new NodeJsRuntimeDockerfileRenderer()),
            List.of(new AwsTerraformRenderer(), new OciTerraformRenderer())
        );
    }

    @Test
    @DisplayName("MySQL → Node.js → Caddy — 포트 입력 없이 파싱하고 CLOUD_DEPLOY에 service, Caddyfile, 80/443 ingress를 만든다")
    void generate_NodeJsWithMysqlAndCaddy_CloudDeployBuildsCaddyArtifacts() {
        // given
        ParsingReqDTO request = new ParsingReqDTO(
            List.of(mysqlNode("db-1"), nodeJsNode("app-1"), caddyNode("caddy-1")),
            List.of(edge("db-1", "app-1"), edge("app-1", "caddy-1"))
        );
        ParsingResultDTO parsingResult = parsingService.parsing(request, 1L);

        // when
        IaCFileDTO.BundleResDTO bundle = cloudGenerator.generate(parsingResult, awsTarget());

        // then
        String compose = fileContent(bundle, "cloud/docker-compose.cloud.yml");
        assertAll(
            () -> assertEquals("""
                example.com {
                    reverse_proxy app:3000
                }
                """, fileContent(bundle, "cloud/caddy/Caddyfile")),
            () -> assertTrue(compose.contains("  caddy:\n    image: caddy:2.8\n")),
            () -> assertTrue(compose.contains("      - caddy_data:/data\n")),
            () -> assertTrue(compose.contains("\nvolumes:\n  mysql_data:\n  caddy_data:\n")),
            () -> assertTrue(fileContent(bundle, "cloud/aws/terraform/main.tf").contains("from_port   = 443"))
        );
    }

    private static NodeDTO caddyNode(String nodeId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("imageVersion", "caddy:2.8");
        properties.put("containerName", "caddy");
        properties.put("domain", "example.com");
        properties.put("volumeName", "caddy_data");

        return new NodeDTO(nodeId, "CADDY", 700f, 200f, properties);
    }

    private static NodeDTO mysqlNode(String nodeId) {
        Map<String, Object> env = new HashMap<>();
        env.put("databaseName", "appdb");
        env.put("username", "user");
        env.put("userPassword", "userpass12");
        env.put("rootPassword", "rootpass12");

        Map<String, Object> properties = new HashMap<>();
        properties.put("imageVersion", "mysql:8.0");
        properties.put("containerName", "mysql");
        properties.put("volumeName", "mysql_data");
        properties.put("port", 3306);
        properties.put("env", env);

        return new NodeDTO(nodeId, "MYSQL", 100f, 200f, properties);
    }

    private static NodeDTO nodeJsNode(String nodeId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("name", "api");
        properties.put("port", 3000);
        properties.put("nodeVersion", "22");
        properties.put("containerName", "node-app");

        return new NodeDTO(nodeId, "NODE_JS", 400f, 200f, properties);
    }

    private static EdgeDTO edge(String sourceNodeId, String targetNodeId) {
        EdgeDTO edge = new EdgeDTO();
        edge.setSourceNodeId(sourceNodeId);
        edge.setTargetNodeId(targetNodeId);
        return edge;
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

    private static String fileContent(IaCFileDTO.BundleResDTO bundle, String fileName) {
        return bundle.files().stream()
            .filter(file -> fileName.equals(file.fileName()))
            .findFirst()
            .orElseThrow()
            .content();
    }
}
