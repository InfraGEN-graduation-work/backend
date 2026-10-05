package com.infragen.infragen.domain.generation.generator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import com.infragen.infragen.domain.parsing.dto.response.NginxComponent;
import com.infragen.infragen.domain.generation.service.command.GenerationCommandService;
import com.infragen.infragen.domain.generation.service.IaCGenerationService;
import com.infragen.infragen.domain.generation.validator.DeploymentTargetValidator;
import com.infragen.infragen.domain.generation.dto.request.GenerateReqDTO;
import com.infragen.infragen.domain.generation.enums.DeploymentOption;
import com.infragen.infragen.domain.project.service.command.ProjectHistoryCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;
import com.infragen.infragen.domain.project.converter.GeneratedFileConverter;
import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.infragen.infragen.domain.generation.dto.request.DeploymentTargetReqDTO;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.domain.parsing.service.ParsingService;
import com.infragen.infragen.domain.parsing.validator.ValidateGraphStructure;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = NginxGenerationContractTest.Config.class)
@DisplayName("NGINX 그래프 파싱과 산출물 계약 — 외부 인프라 없이 검증")
class NginxGenerationContractTest {
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = {
        "com.infragen.infragen.domain.parsing",
        "com.infragen.infragen.domain.generation.generator"
    })
    static class Config {
        @Bean
        org.springframework.validation.beanvalidation.LocalValidatorFactoryBean validator() {
            return new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private ParsingService parsingService;
    @Autowired
    private ValidateGraphStructure validator;
    @Autowired
    private DockerComposeIaCGenerator localGenerator;
    @Autowired
    private TerraformIaCGenerator cloudGenerator;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private jakarta.validation.Validator beanValidator;

    @ParameterizedTest
    @ValueSource(strings = {"nginx", "NGINX", "edge-proxy"})
    void validate_ConflictingInfrastructureName_RejectsGraph(String containerName) {
        // given
        var request = request(9090, 80);
        var nodes = new ArrayList<>(request.getNodes());
        nodes.add(new NodeDTO("cache", "REDIS", 0f, 0f, Map.of("containerName", containerName)));
        // when
        ParsingException exception = assertThrows(ParsingException.class,
            () -> validator.validate(nodes, request.getEdges()));
        // then
        assertEquals(ParsingErrorCode.INVALID_NGINX_PROPERTIES, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void generate_Nginx_SavesConfigurationWithOtherFiles(boolean includeCloud) {
        // given
        var history = mock(ProjectHistoryCommandService.class);
        var service = new GenerationCommandService(mock(ProjectQueryService.class), parsingService,
            new IaCGenerationService(List.of(localGenerator), List.of(cloudGenerator)), history,
            new DeploymentTargetValidator(beanValidator));
        var graph = request(9090, 80);
        var request = new GenerateReqDTO.Request(graph.getNodes(), graph.getEdges(),
            includeCloud ? DeploymentOption.AWS : DeploymentOption.LOCAL, includeCloud,
            includeCloud ? cloudTargets().findFirst().orElseThrow() : null);
        when(history.saveGeneratedHistory(eq(1L), eq(2L), anyList())).thenReturn(42L);
        // when
        var result = service.generate(1L, request, 2L);
        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IaCFileDTO.FileContentResDTO>> files = ArgumentCaptor.forClass(List.class);
        verify(history).saveGeneratedHistory(eq(1L), eq(2L), files.capture());
        var config = files.getValue().stream()
            .filter(file -> file.fileName().equals("local/nginx/default.conf")).findFirst().orElseThrow();
        var entity = GeneratedFileConverter.toEntity(config, 1L, "v1");
        assertAll(
            () -> assertEquals(42L, result.historyId()),
            () -> assertEquals(includeCloud ? 10 : 3, result.files().size()),
            () -> assertEquals(includeCloud ? 2 : 1, files.getValue().stream()
                .filter(file -> file.fileName().endsWith("nginx/default.conf")).count()),
            () -> assertEquals("projects/1/histories/v1/local/nginx/default.conf", entity.getFilePath()),
            () -> assertEquals(config.content(), entity.getContent()),
            () -> assertTrue(result.files().stream().anyMatch(file ->
                file.fileName().equals(config.fileName()) && file.content().equals(config.content())))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 80, 65535})
    void parsing_NginxPortBoundary_AcceptsValidPort(int port) {
        // given
        ParsingReqDTO request = request(9090, port);
        // when
        ParsingResultDTO result = parsingService.parsing(request, 1L);
        // then
        assertEquals(port, ((NginxComponent) result.getComponents().get(1)).getPort());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "fractional", "string", "overflow"})
    void parsing_NonIntegerPort_RejectsInput(String scenario) {
        // given
        ParsingReqDTO request = request(9090, 80);
        Map<String, Object> props = new HashMap<>(request.getNodes().get(1).getProperties());
        props.remove("port");
        if (scenario.equals("fractional")) props.put("port", 80.5);
        if (scenario.equals("string")) props.put("port", "80");
        if (scenario.equals("overflow")) props.put("port", 4294967376L);
        request.getNodes().get(1).getProperties().clear();
        request.getNodes().get(1).getProperties().putAll(props);
        // when
        ParsingException exception = assertThrows(ParsingException.class,
            () -> parsingService.parsing(request, 1L));
        // then
        assertEquals(ParsingErrorCode.INVALID_NGINX_PORT, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"disconnected", "twoApps", "twoProxies", "databaseSource", "noApp"})
    void validate_InvalidProxyTopology_RejectsGraph(String scenario) {
        // given
        ParsingReqDTO valid = request(9090, 80);
        List<NodeDTO> nodes = new ArrayList<>(valid.getNodes());
        List<EdgeDTO> edges = new ArrayList<>(valid.getEdges());
        switch (scenario) {
            case "disconnected" -> edges.clear();
            case "twoApps" -> nodes.add(new NodeDTO("app2", "SPRING_BOOT", 0f, 0f, Map.of()));
            case "twoProxies" -> nodes.add(new NodeDTO("proxy2", "NGINX", 0f, 0f, Map.of()));
            case "databaseSource" -> {
                nodes.add(new NodeDTO("db", "MYSQL", 0f, 0f, Map.of()));
                edges.add(edge("db", "proxy"));
            }
            case "noApp" -> nodes.removeFirst();
            default -> throw new AssertionError(scenario);
        }
        // when
        ParsingException exception = assertThrows(ParsingException.class,
            () -> validator.validate(nodes, edges));
        // then
        assertEquals(ParsingErrorCode.INVALID_NGINX_CONNECTION, exception.getCode());
    }

    @Test
    void generate_DuplicateProxyEdge_GeneratesOneConfiguration() {
        // given
        ParsingReqDTO request = request(9090, 80);
        request = new ParsingReqDTO(request.getNodes(),
            List.of(edge("application", "proxy"), edge("application", "proxy")));
        // when
        var bundle = localGenerator.generate(parsingService.parsing(request, 1L));
        // then
        assertEquals(3, bundle.files().size());
    }

    @ParameterizedTest
    @MethodSource("cloudTargets")
    void generate_NginxCloud_ExposesOnlyProxyAndOpensTerraformPort(DeploymentTargetReqDTO.Target target) {
        // given
        ParsingReqDTO request = request(9090, 80);
        String provider = target.provider().name().toLowerCase(java.util.Locale.ROOT);
        // when
        var bundle = cloudGenerator.generate(parsingService.parsing(request, 1L), target);
        // then
        String compose = file(bundle, "cloud/docker-compose.cloud.yml");
        String appBlock = compose.substring(compose.indexOf("  app:"), compose.indexOf("  nginx:"));
        assertAll(
            () -> assertFalse(appBlock.contains("ports:")),
            () -> assertFalse(appBlock.contains("depends_on:")),
            () -> assertTrue(compose.contains("\"80:80\"")),
            () -> assertTrue(compose.contains("depends_on:\n      - app")),
            () -> assertTrue(file(bundle, "cloud/" + provider + "/terraform/terraform.tfvars.example")
                .contains("app_port = 80")),
            () -> assertTrue(file(bundle, "cloud/" + provider + "/terraform/variables.tf")
                .contains("default     = 80")),
            () -> assertTrue(file(bundle, "cloud/" + provider + "/terraform/main.tf")
                .contains("var.app_port")),
            () -> assertTrue(file(bundle, "cloud/Dockerfile").contains("EXPOSE 9090"))
        );
    }

    @ParameterizedTest
    @MethodSource("cloudTargets")
    @DisplayName("NGINX와 MongoDB를 함께 연결해도 앱 환경변수와 프록시를 생성한다")
    void generate_nginxWithMongo_preservesDatabaseEnvironmentAndProxy(DeploymentTargetReqDTO.Target target) {
        // given
        ParsingReqDTO base = request(9090, 80);
        List<NodeDTO> nodes = new ArrayList<>(base.getNodes());
        nodes.add(new NodeDTO("mongo", "MONGODB", 0f, 0f, Map.of(
            "imageVersion", "mongo:8", "port", 27017, "containerName", "mongo-db",
            "env", Map.of("databaseName", "appdb", "username", "testuser", "password", "test-only-password"))));
        ParsingReqDTO request = new ParsingReqDTO(nodes,
            List.of(edge("application", "proxy"), edge("mongo", "application")));

        // when
        var bundle = cloudGenerator.generate(parsingService.parsing(request, 1L), target);

        // then
        String compose = file(bundle, "cloud/docker-compose.cloud.yml");
        String appBlock = compose.substring(compose.indexOf("  app:"), compose.indexOf("\n  mongodb:"));
        assertAll(
            () -> assertFalse(appBlock.contains("ports:")),
            () -> assertTrue(appBlock.contains("SPRING_MONGODB_HOST: \"mongodb\"")),
            () -> assertTrue(appBlock.contains("depends_on:\n      - mongodb")),
            () -> assertTrue(compose.contains("\"80:80\"")),
            () -> assertTrue(file(bundle, "cloud/nginx/default.conf").contains("proxy_pass http://app:9090;"))
        );
    }

    static Stream<DeploymentTargetReqDTO.Target> cloudTargets() {
        return Stream.of(
            new DeploymentTargetReqDTO.AwsDeploymentTarget("ap-northeast-2", "vpc", "subnet", "igw",
                "route", "sg", "instance", "10.0.0.0/16", "10.0.1.0/24", "ami-xxxxxxxx",
                "t3.micro", "203.0.113.10/32", "0.0.0.0/0"),
            new DeploymentTargetReqDTO.OciDeploymentTarget("ap-chuncheon-1", "vcn", "subnet", "igw",
                "route", "security", "instance", "app", "ocid1.compartment.oc1..example",
                "AD-1", "ocid1.image.oc1..example", "VM.Standard.E4.Flex", "10.0.0.0/16",
                "10.0.1.0/24", "203.0.113.10/32", "0.0.0.0/0", "ssh-rsa TEST_ONLY"));
    }

    @Test
    @DisplayName("앱에서 NGINX로 연결한 노드의 속성을 파싱한다")
    void parsing_ApplicationToNginx_PreservesProperties() {
        // given
        ParsingReqDTO request = request(9090, 8081);

        // when
        ParsingResultDTO result = parsingService.parsing(request, 1L);

        // then
        var nginx = result.getComponents().stream()
            .filter(component -> component.getComponentType() == ComponentType.NGINX)
            .findFirst().orElseThrow();
        var properties = objectMapper.valueToTree(nginx);
        assertAll(
            () -> assertEquals("proxy", nginx.getNodeId()),
            () -> assertEquals("nginx:stable", properties.path("imageVersion").asText()),
            () -> assertEquals("edge-proxy", properties.path("containerName").asText()),
            () -> assertEquals(8081, properties.path("port").asInt()),
            () -> assertEquals(2, result.getComponents().size()),
            () -> assertEquals(request.getEdges(), result.getEdges())
        );
    }

    @Test
    @DisplayName("NGINX에서 앱으로 향하는 역방향 연결을 거부한다")
    void validate_NginxToApplication_RejectsReverseDirection() {
        // given
        ParsingReqDTO request = request(9090, 8081);

        // when
        ParsingException exception = assertThrows(ParsingException.class,
            () -> validator.validate(request.getNodes(), List.of(edge("proxy", "application"))));

        // then
        assertEquals(ParsingErrorCode.INVALID_COMPONENT_DEPENDENCY, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 65536})
    @DisplayName("NGINX의 유효하지 않은 포트를 포트 검증 오류로 거부한다")
    void parsing_InvalidNginxPort_RejectsPort(int port) {
        // given
        ParsingReqDTO request = request(9090, port);

        // when
        ParsingException exception = assertThrows(ParsingException.class,
            () -> parsingService.parsing(request, 1L));

        // then
        assertEquals(ParsingErrorCode.INVALID_NGINX_PORT, exception.getCode());
    }

    @Test
    @DisplayName("앱과 NGINX의 호스트 포트가 중복되면 거부한다")
    void parsing_DuplicateNginxPort_RejectsDuplicatePort() {
        // given
        ParsingReqDTO request = request(9090, 9090);

        // when
        ParsingException exception = assertThrows(ParsingException.class,
            () -> parsingService.parsing(request, 1L));

        // then
        assertEquals(ParsingErrorCode.DUPLICATE_PORT, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {8080, 9090})
    @DisplayName("LOCAL_DEV는 호스트 앱 포트로 프록시하고 Linux 호스트 매핑을 생성한다")
    void generate_LocalDevNginx_ProxiesToHostApplication(int appPort) {
        // given
        ParsingReqDTO request = request(appPort, 8081);

        // when
        IaCFileDTO.BundleResDTO bundle = localGenerator.generate(parsingService.parsing(request, 1L));

        // then
        String compose = file(bundle, "local/docker-compose.yml");
        IaCFileDTO.FileContentResDTO config = proxyConfig(bundle, "local/");
        assertAll(
            () -> assertTrue(compose.contains("image: nginx:stable")),
            () -> assertTrue(compose.contains("container_name: edge-proxy")),
            () -> assertTrue(compose.contains("extra_hosts:")),
            () -> assertTrue(compose.contains("host.docker.internal:host-gateway")),
            () -> assertTrue(compose.contains(config.fileName().substring("local/".length()))),
            () -> assertFalse(compose.contains("  app:\n")),
            () -> assertTrue(config.content().matches(
                "(?s).*proxy_pass\\s+http://host\\.docker\\.internal:" + appPort + "/?\\s*;.*"))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {8080, 9090})
    @DisplayName("CLOUD_DEPLOY는 앱 컨테이너 포트로 프록시하는 설정 파일을 생성한다")
    void generate_CloudNginx_ProxiesToApplicationContainer(int appPort) {
        // given
        ParsingReqDTO request = request(appPort, 8081);
        var target = new DeploymentTargetReqDTO.AwsDeploymentTarget(
            "ap-northeast-2", "vpc", "subnet", "igw", "route", "sg", "instance",
            "10.0.0.0/16", "10.0.1.0/24", "ami-xxxxxxxx", "t3.micro",
            "203.0.113.10/32", "0.0.0.0/0");

        // when
        IaCFileDTO.BundleResDTO bundle = cloudGenerator.generate(
            parsingService.parsing(request, 1L), target);

        // then
        String compose = file(bundle, "cloud/docker-compose.cloud.yml");
        IaCFileDTO.FileContentResDTO config = proxyConfig(bundle, "cloud/");
        assertAll(
            () -> assertTrue(compose.contains("image: nginx:stable")),
            () -> assertTrue(compose.contains("container_name: edge-proxy")),
            () -> assertTrue(compose.contains("  app:\n")),
            () -> assertTrue(compose.contains(config.fileName().substring("cloud/".length()))),
            () -> assertFalse(config.content().contains("host.docker.internal")),
            () -> assertTrue(config.content().matches(
                "(?s).*proxy_pass\\s+http://app:" + appPort + "/?\\s*;.*"))
        );
    }

    static ParsingReqDTO request(int appPort, int nginxPort) {
        return new ParsingReqDTO(List.of(
            new NodeDTO("application", "SPRING_BOOT", 0f, 0f, Map.of(
                "name", "app", "port", appPort, "javaVersion", "21", "containerName", "app")),
            new NodeDTO("proxy", "NGINX", 100f, 0f, new HashMap<>(Map.of(
                "imageVersion", "nginx:stable", "port", nginxPort, "containerName", "edge-proxy")))
        ), List.of(edge("application", "proxy")));
    }

    private static EdgeDTO edge(String source, String target) {
        EdgeDTO edge = new EdgeDTO();
        edge.setSourceNodeId(source);
        edge.setTargetNodeId(target);
        return edge;
    }

    private static String file(IaCFileDTO.BundleResDTO bundle, String name) {
        return bundle.files().stream().filter(file -> file.fileName().equals(name))
            .findFirst().orElseThrow(() -> new AssertionError("생성 파일 누락: " + name)).content();
    }

    private static IaCFileDTO.FileContentResDTO proxyConfig(IaCFileDTO.BundleResDTO bundle, String scope) {
        List<IaCFileDTO.FileContentResDTO> configs = bundle.files().stream()
            .filter(file -> file.fileName().startsWith(scope) && file.fileName().endsWith(".conf"))
            .toList();
        assertEquals(1, configs.size(), "단일 앱용 NGINX 설정 파일이 하나 생성되어야 한다");
        return configs.getFirst();
    }
}
