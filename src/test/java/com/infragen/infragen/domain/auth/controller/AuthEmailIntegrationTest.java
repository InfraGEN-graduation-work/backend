package com.infragen.infragen.domain.auth.controller;

import com.infragen.infragen.InfragenApplication;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = InfragenApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.mail.username=",
        "spring.mail.password=",
        "spring.mail.properties[mail.smtp.auth]=false",
        "spring.mail.properties[mail.smtp.starttls.enable]=false",
        "spring.mail.properties[mail.smtp.starttls.required]=false",
        "MAIL_FROM=signup@infragen.test",
        "jwt.secret=integration-test-secret-integration-test-secret-1234567890",
        "kakao.client-id=test",
        "kakao.client-secret=test",
        "kakao.redirect-uri=http://localhost/test-callback"
})
class AuthEmailIntegrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("email_integration")
            .withUsername("email_test")
            .withPassword("email_test_password");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Container
    private static final GenericContainer<?> SMTP = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27.4"))
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.mail.host", SMTP::getHost);
        registry.add("spring.mail.port", () -> SMTP.getMappedPort(1025));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper mapper = new JsonMapper();

    @Test
    @DisplayName("DB에 가입된 이메일은 409를 반환하고 SMTP 발송이나 Redis 키 생성이 없다")
    void sendEmailCode_ExistingEmail_ReturnsConflictWithoutDelivery() throws Exception {
        // given
        String email = "existing@infragen.test";
        memberRepository.saveAndFlush(Member.builder().email(email).password("test-only-password")
                .nickname("existing").role(Role.ROLE_USER).isActive(true).build());
        int messageCount = mailbox("/api/v1/messages").path("total").asInt();
        var keys = redisTemplate.keys("signup-email:*");

        // when
        var response = post("/email/code", Map.of("email", email));

        // then
        JsonNode body = mapper.readTree(response.body());
        assertAll(
                () -> assertEquals(409, response.statusCode()),
                () -> assertFalse(body.path("isSuccess").asBoolean()),
                () -> assertEquals("MEMBER409_1", body.path("code").asText()),
                () -> assertEquals("이미 가입된 이메일입니다.", body.path("message").asText()),
                () -> assertEquals(messageCount, mailbox("/api/v1/messages").path("total").asInt()),
                () -> assertEquals(keys, redisTemplate.keys("signup-email:*"))
        );
    }

    @Test
    @DisplayName("신규 이메일은 실제 SMTP로 인증번호를 받고 Redis 인증을 거쳐 MySQL에 가입된다")
    void signup_NewEmail_CompletesWithDeliveredCodeThenReturnsDuplicate() throws Exception {
        // given
        String email = "new@infragen.test";
        assertFalse(memberRepository.existsByEmail(email));

        // when
        var sent = post("/email/code", Map.of("email", email));

        // then
        assertEquals(200, sent.statusCode());
        assertEquals("AUTH200_4", mapper.readTree(sent.body()).path("code").asText());
        JsonNode messages = mailbox("/api/v1/messages").path("messages");
        assertEquals(1, messages.size());
        JsonNode message = mailbox("/api/v1/message/" + messages.get(0).path("ID").asText());
        assertEquals(email, message.path("To").get(0).path("Address").asText());
        var matcher = Pattern.compile("인증번호는 ([0-9]{6}) 입니다").matcher(message.path("Text").asText());
        assertTrue(matcher.find(), "수신 메일에 6자리 인증번호가 있어야 한다");

        // given
        String code = matcher.group(1);
        var codeKeys = redisTemplate.keys("signup-email:*:code");
        assertEquals(1, codeKeys.size());
        String codeKey = codeKeys.iterator().next();
        assertTrue(redisTemplate.getExpire(codeKey) > 0);

        // when
        var signup = post("/signup", Map.of("email", email, "verificationCode", code,
                "password", "test-password123", "nickname", "new-member"));

        // then
        assertEquals(201, signup.statusCode());
        assertEquals("AUTH201_1", mapper.readTree(signup.body()).path("code").asText());
        assertTrue(memberRepository.existsByEmail(email));
        assertFalse(redisTemplate.hasKey(codeKey));

        // when
        var duplicate = post("/email/code", Map.of("email", email));

        // then
        assertEquals(409, duplicate.statusCode());
        assertEquals("MEMBER409_1", mapper.readTree(duplicate.body()).path("code").asText());
        assertEquals(1, mailbox("/api/v1/messages").path("total").asInt());
    }

    private HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth" + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode mailbox(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + SMTP.getHost() + ":"
                + SMTP.getMappedPort(8025) + path)).timeout(Duration.ofSeconds(10)).GET().build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return mapper.readTree(response.body());
    }
}
