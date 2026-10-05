package com.infragen.infragen.domain.generation.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.ToolProvider;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.parsing.service.ParsingService;

/** Docker 엔진과 이미지 다운로드가 필요한 명시적 실행용 테스트다. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = NginxGenerationContractTest.Config.class)
class NginxProxyIntegrationTest {
    @TempDir
    Path directory;
    @Autowired
    ParsingService parser;
    @Autowired
    DockerComposeIaCGenerator local;
    @Autowired
    TerraformIaCGenerator cloud;

    @Test
    void generate_LocalCompose_ProxiesHttpToHost() throws Exception {
        // given
        HttpServer app = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
        app.createContext("/", exchange -> {
            byte[] response = ("nginx-ok " + exchange.getRequestURI()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) { body.write(response); }
        });
        app.start();
        int proxyPort = freePort();
        String project = "nginx-local-" + UUID.randomUUID().toString().substring(0, 8);
        var request = NginxGenerationContractTest.request(app.getAddress().getPort(), proxyPort);
        request.getNodes().get(1).getProperties().put("containerName", project);
        Path compose = directory.resolve("local/docker-compose.yml");
        try {
            // when
            write(local.generate(parser.parsing(request, 1L)));
            docker(compose, project, "config", "--quiet");
            docker(compose, project, "up", "-d");
            String response = awaitHttp(proxyPort);
            // then
            assertEquals("nginx-ok /health?probe=1", response);
            docker(compose, project, "exec", "-T", "nginx", "nginx", "-t");
        } finally {
            try { docker(compose, project, "down", "--volumes", "--remove-orphans"); }
            finally { app.stop(0); }
        }
    }

    @Test
    void generate_CloudCompose_ProxiesHttpToAppContainer() throws Exception {
        // given
        int proxyPort = freePort();
        String project = "nginx-cloud-" + UUID.randomUUID().toString().substring(0, 8);
        var request = NginxGenerationContractTest.request(9090, proxyPort);
        request.getNodes().get(1).getProperties().put("containerName", project);
        Path compose = directory.resolve("cloud/docker-compose.cloud.yml");
        try {
            // when
            write(cloud.generate(parser.parsing(request, 1L),
                NginxGenerationContractTest.cloudTargets().findFirst().orElseThrow()));
            Files.writeString(directory.resolve("cloud/.env"), "");
            createApplicationJar(directory.resolve("cloud"));
            // 생성된 Dockerfile은 그대로 빌드하되 테스트 이미지 이름만 격리한다.
            Files.writeString(directory.resolve("cloud/test-image.yml"),
                "services:\n  app:\n    image: " + project + ":test\n");
            docker(compose, project, "config", "--quiet");
            docker(compose, project, "up", "-d", "--build");
            String response = awaitHttp(proxyPort);
            // then
            assertEquals("nginx-ok /health?probe=1", response);
            docker(compose, project, "exec", "-T", "nginx", "nginx", "-t");
            var status = new tools.jackson.databind.ObjectMapper().readTree(
                docker(compose, project, "ps", "--format", "json", "app"));
            var appStatus = status.isArray() ? status.get(0) : status;
            assertEquals("app", appStatus.path("Service").asString());
            for (var publisher : appStatus.path("Publishers")) {
                assertEquals(0, publisher.path("PublishedPort").asInt(), "앱 포트가 외부에 공개되면 안 된다");
            }
        } finally {
            docker(compose, project, "down", "--volumes", "--remove-orphans", "--rmi", "local");
        }
    }

    private void write(IaCFileDTO.BundleResDTO bundle) throws IOException {
        for (var file : bundle.files()) {
            Path target = directory.resolve(file.fileName());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.content());
        }
    }

    private String docker(Path compose, String project, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("docker", "compose", "-p", project,
            "-f", compose.toString()));
        Path override = compose.resolveSibling("test-image.yml");
        if (Files.exists(override)) command.addAll(List.of("-f", override.toString()));
        command.addAll(List.of(arguments));
        return runCommand(command);
    }

    private String runCommand(List<String> command) throws Exception {
        Path log = Files.createTempFile(directory, "docker-", ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(log.toFile()).start();
        boolean finished = process.waitFor(180, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertTrue(finished, "Docker 명령 시간 초과: " + command);
        String output = Files.readString(log);
        assertEquals(0, process.exitValue(), command + "\n" + output);
        return output;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private static String awaitHttp(int port) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health?probe=1"))
            .timeout(Duration.ofSeconds(2)).build();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        String last = "no response";
        while (System.nanoTime() < deadline) {
            try {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) return response.body();
                last = response.statusCode() + ": " + response.body();
            } catch (IOException exception) { last = exception.toString(); }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
        }
        throw new AssertionError("NGINX 접속 실패: " + last);
    }

    private static void createApplicationJar(Path root) throws IOException {
        Path source = root.resolve("Main.java");
        Files.writeString(source, """
            import com.sun.net.httpserver.HttpServer;
            import java.net.InetSocketAddress;
            import java.nio.charset.StandardCharsets;
            public class Main {
                public static void main(String[] args) throws Exception {
                    var server = HttpServer.create(new InetSocketAddress("0.0.0.0", 9090), 0);
                    server.createContext("/", exchange -> {
                        byte[] body = ("nginx-ok " + exchange.getRequestURI()).getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, body.length);
                        try (var output = exchange.getResponseBody()) { output.write(body); }
                    });
                    server.start();
                }
            }
            """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
            "--release", "21", "-d", root.toString(), source.toString()));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "Main");
        try (var jar = new JarOutputStream(Files.newOutputStream(root.resolve("app.jar")), manifest)) {
            jar.putNextEntry(new JarEntry("Main.class"));
            Files.copy(root.resolve("Main.class"), jar);
            jar.closeEntry();
        }
    }
}
