package com.infragen.infragen.global.auth.websocket;

import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.global.auth.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompTokenRevalidationIntegrationTest {
    private static final String REDIS_PASSWORD = "test-redis-password";
    private static final String ACCESS_TOKEN = "access-token";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse(
                    "mysql:8.4.6@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9"
            ).asCompatibleSubstituteFor("mysql")
    )
            .withDatabaseName("infragen_stomp_revalidation_test")
            .withUsername("infragen_test")
            .withPassword("infragen_test_password");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
        registry.add("jwt.secret", () -> "test-jwt-secret-test-jwt-secret-test-jwt-secret-1234567890");
        registry.add("kakao.client-id", () -> "test-kakao-client-id");
        registry.add("kakao.client-secret", () -> "test-kakao-client-secret");
        registry.add("kakao.redirect-uri", () -> "http://localhost/test-callback");
        registry.add("kakao.authorization-uri", () -> "http://localhost/kakao/authorize");
        registry.add("kakao.token-uri", () -> "http://localhost/kakao/token");
        registry.add("kakao.user-info-uri", () -> "http://localhost/kakao/user-info");
    }

    @LocalServerPort
    private int serverPort;

    @MockitoBean
    private StompAccessTokenAuthenticator tokenAuthenticator;

    @Test
    @DisplayName("연결 후 token 재검증이 실패하면 다음 STOMP message를 거부한다")
    void sendAfterTokenRevalidationFailure_ClosesStompSession() throws Exception {
        // given
        Authentication authentication = authentication();
        CountDownLatch transportError = new CountDownLatch(1);
        when(tokenAuthenticator.authenticate(ACCESS_TOKEN))
                .thenReturn(authentication)
                .thenThrow(new com.infragen.infragen.domain.auth.exception.AuthException(
                        com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode.TOKEN_INVALID
                ));
        StompSession session = connect(transportError);

        // when
        session.subscribe("/topic/projects/1/operations", new StompSessionHandlerAdapter() {
        });

        // then
        assertTrue(transportError.await(5, TimeUnit.SECONDS));
    }

    private StompSession connect(CountDownLatch transportError) throws Exception {
        WebSocketStompClient webSocketStompClient = new WebSocketStompClient(new StandardWebSocketClient());
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + ACCESS_TOKEN);
        return webSocketStompClient.connectAsync(
                        "ws://localhost:" + serverPort + "/ws/collaboration",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {
                            @Override
                            public void handleTransportError(StompSession session, Throwable exception) {
                                transportError.countDown();
                            }
                        }
                )
                .get(10, TimeUnit.SECONDS);
    }

    private Authentication authentication() {
        CustomUserDetails userDetails = new CustomUserDetails(
                MemberResDTO.MemberResultDTO.builder()
                        .id(1L)
                        .role(Role.ROLE_USER)
                        .isActive(true)
                        .build()
        );
        return new UsernamePasswordAuthenticationToken(
                userDetails,
                ACCESS_TOKEN,
                List.of()
        );
    }
}
