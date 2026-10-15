package com.infragen.infragen.domain.generation.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.infragen.infragen.domain.generation.generator.cloud.CloudComposeRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.MysqlCloudComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.NodeJsRuntimeDockerfileRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.OciTerraformRenderer;
import com.infragen.infragen.domain.generation.generator.cloud.SpringBootRuntimeDockerfileRenderer;
import com.infragen.infragen.domain.generation.generator.compose.MysqlComposeServiceRenderer;
import com.infragen.infragen.domain.generation.generator.compose.MysqlHostAppEnvContributor;
import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.parser.MySQLParser;
import com.infragen.infragen.domain.parsing.parser.NodeJsParser;
import com.infragen.infragen.domain.parsing.parser.SpringBootParser;
import com.infragen.infragen.domain.parsing.service.ParsingService;
import com.infragen.infragen.domain.parsing.validator.ValidateGraphStructure;

import tools.jackson.databind.ObjectMapper;

/**
 * NODE_JS 그래프가 parsing부터 LOCAL_DEV, CLOUD_DEPLOY 생성까지 이어지는지 확인한다.
 * Spring Boot 산출물 회귀는 각 generator 테스트의 golden이 맡는다.
 */
@DisplayName("Node.js 그래프 Generate 계약")
class NodeJsGenerationContractTest {

    private ParsingService parsingService;
    private DockerComposeIaCGenerator localGenerator;
    private TerraformIaCGenerator cloudGenerator;

    @BeforeEach
    void setUp() {
        parsingService = new ParsingService(
            new ObjectMapper(),
            new ValidateGraphStructure(),
            List.of(new SpringBootParser(), new NodeJsParser(), new MySQLParser())
        );
        localGenerator = new DockerComposeIaCGenerator(
            List.of(new MysqlComposeServiceRenderer()),
            List.of(new MysqlHostAppEnvContributor()),
            List.of(new SpringBootApplicationEnvMapper(), new NodeJsApplicationEnvMapper())
        );
        cloudGenerator = new TerraformIaCGenerator(
            new CloudComposeRenderer(
                List.of(new MysqlCloudComposeServiceRenderer()),
                List.of(new SpringBootApplicationEnvMapper(), new NodeJsApplicationEnvMapper())
            ),
            List.of(new SpringBootRuntimeDockerfileRenderer(), new NodeJsRuntimeDockerfileRenderer()),
            List.of(new AwsTerraformRenderer(), new OciTerraformRenderer())
        );
    }

    @Test
    @DisplayName("MySQL → Node.js — LOCAL_DEV는 인프라 Compose와 중립 변수만 만들고 앱은 Compose에 넣지 않는다")
    void generate_NodeJsWithMysql_LocalDevBuildsInfraAndNeutralEnvOnly() {
        // given
        ParsingResultDTO parsingResult = parse();

        // when
        IaCFileDTO.BundleResDTO bundle = localGenerator.generate(parsingResult);

        // then
        String compose = fileContent(bundle, "local/docker-compose.yml");
        String env = fileContent(bundle, "local/.env");
        assertEquals(List.of("local/docker-compose.yml", "local/.env"), fileNames(bundle));
        assertTrue(compose.contains("mysql:"));
        assertFalse(compose.contains("node-app"));
        assertTrue(env.contains("MYSQL_HOST=localhost\n"));
        assertTrue(env.contains("MYSQL_PASSWORD=userpass12\n"));
        assertFalse(env.contains("SPRING_"));
    }

    @Test
    @DisplayName("MySQL → Node.js — CLOUD_DEPLOY는 Node.js Dockerfile과 .dockerignore를 만들고 .env는 제외한다")
    void generate_NodeJsWithMysql_CloudDeployBuildsNodeRuntimeAndDockerignore() {
        // given
        ParsingResultDTO parsingResult = parse();

        // when
        IaCFileDTO.BundleResDTO bundle = cloudGenerator.generate(parsingResult, awsTarget());

        // then
        assertEquals(7, bundle.files().size());
        assertEquals("cloud/Dockerfile", bundle.files().get(3).fileName());
        assertEquals("cloud/.dockerignore", bundle.files().get(4).fileName());
        assertEquals("cloud/docker-compose.cloud.yml", bundle.files().get(5).fileName());
        assertTrue(fileContent(bundle, "cloud/Dockerfile").contains("FROM node:22-slim"));
        assertTrue(fileContent(bundle, "cloud/Dockerfile").contains("CMD [\"npm\", \"start\"]"));
        assertTrue(fileContent(bundle, "cloud/.dockerignore").contains("**/.env"));
        String warning = fileContent(bundle, "cloud/CLOUD_DEPLOY_WARNING.md");
        assertTrue(warning.contains("`package.json`, `package-lock.json`, 앱 파일"));
        assertFalse(warning.contains("app.jar"));
        assertTrue(bundle.files().stream().noneMatch(file -> file.content().contains("Spring Boot")));
        String compose = fileContent(bundle, "cloud/docker-compose.cloud.yml");
        assertTrue(compose.contains("${APP_PORT:-3000}:3000"));
        assertTrue(compose.contains("      PORT: \"3000\"\n"));
        assertTrue(compose.contains("MYSQL_HOST: mysql"));
        assertFalse(compose.contains("SPRING_"));
    }

    private ParsingResultDTO parse() {
        ParsingReqDTO request = new ParsingReqDTO(
            List.of(mysqlNode("node-1"), nodeJsNode("node-2")),
            List.of(edge("node-1", "node-2"))
        );
        return parsingService.parsing(request, 1L);
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

    private static List<String> fileNames(IaCFileDTO.BundleResDTO bundle) {
        return bundle.files().stream().map(IaCFileDTO.FileContentResDTO::fileName).toList();
    }

    private static String fileContent(IaCFileDTO.BundleResDTO bundle, String fileName) {
        return bundle.files().stream()
            .filter(file -> fileName.equals(file.fileName()))
            .findFirst()
            .orElseThrow()
            .content();
    }
}
