package com.infragen.infragen.global.auth.websocket;

import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import com.infragen.infragen.global.auth.CustomUserDetails;
import com.infragen.infragen.global.util.JwtUtil;
import com.infragen.infragen.global.util.RedisUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StompProjectOutboundInterceptorTest {
    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
    private static final String TOKEN = "access-token";

    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private MessageChannel channel;
    @Mock
    private MessageHandler handler;

    private StompProjectOutboundInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompProjectOutboundInterceptor(
                projectAccessService, redisUtil, jwtUtil, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @ParameterizedTest
    @ValueSource(strings = {"operations", "resync"})
    @DisplayName("현재 읽기 권한이 있으면 project 방송을 수신 session에 전달한다")
    void beforeHandle_ReadAllowed_DeliversMessage(String topic) {
        // given
        connected("receiver-session", 2L);
        Message<byte[]> message = message(SimpMessageType.MESSAGE, "/topic/projects/10/" + topic, "receiver-session");

        // when
        Message<?> result = interceptor.beforeHandle(message, channel, handler);

        // then
        assertSame(message, result);
        verify(projectAccessService).requireReadAccess(10L, 2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"operations", "resync"})
    @DisplayName("방송 헤더의 발신자가 owner여도 실제 수신자의 권한이 없으면 차단한다")
    void beforeHandle_SenderHeaderIsOwner_ChecksRecipient(String topic) {
        // given
        connected("receiver-session", 2L);
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination("/topic/projects/10/" + topic);
        headers.setSessionId("receiver-session");
        headers.setUser(authentication(1L));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        doThrow(new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).requireReadAccess(10L, 2L);

        // when
        Message<?> result = interceptor.beforeHandle(message, channel, handler);

        // then
        assertNull(result);
        verify(projectAccessService).requireReadAccess(10L, 2L);
    }

    @Test
    @DisplayName("outbound enqueue 이후 권한이 회수되어도 실제 handler 실행 전에 차단한다")
    void beforeHandle_RevokedAfterEnqueue_RechecksAccess() {
        // given
        connected("receiver-session", 2L);
        Message<byte[]> message = message(SimpMessageType.MESSAGE, "/topic/projects/10/operations", "receiver-session");
        Message<?> queued = interceptor.preSend(message, channel);
        verifyNoInteractions(projectAccessService);
        doThrow(new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).requireReadAccess(10L, 2L);

        // when
        Message<?> result = interceptor.beforeHandle(queued, channel, handler);

        // then
        assertNull(result);
        verify(projectAccessService).requireReadAccess(10L, 2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown-session", ""})
    @DisplayName("수신 session을 인증 회원과 연결할 수 없으면 project 방송을 차단한다")
    void beforeHandle_UnknownSession_BlocksMessage(String sessionId) {
        // given
        Message<byte[]> message = message(SimpMessageType.MESSAGE, "/topic/projects/10/resync", sessionId);

        // when
        Message<?> result = interceptor.beforeHandle(message, channel, handler);

        // then
        assertNull(result);
        verifyNoInteractions(projectAccessService);
    }

    @Test
    @DisplayName("disconnect 이벤트가 중복되어도 session 연결 정보를 제거한다")
    void beforeHandle_DisconnectedSession_BlocksMessage() {
        // given
        connected("receiver-session", 2L);
        Message<byte[]> message = message(SimpMessageType.MESSAGE, "/topic/projects/10/resync", "receiver-session");
        SessionDisconnectEvent disconnected = new SessionDisconnectEvent(this, message, "receiver-session", CloseStatus.NORMAL);
        interceptor.onSessionDisconnected(disconnected);
        interceptor.onSessionDisconnected(disconnected);

        // when
        Message<?> result = interceptor.beforeHandle(message, channel, handler);

        // then
        assertNull(result);
        verifyNoInteractions(projectAccessService);
    }

    @Test
    @DisplayName("인증되지 않은 Principal은 수신 session으로 등록하지 않는다")
    void beforeHandle_UnauthenticatedPrincipal_BlocksMessage() {
        // given
        Message<byte[]> connect = message(SimpMessageType.CONNECT_ACK, null, "receiver-session");
        Authentication anonymous = new UsernamePasswordAuthenticationToken(authentication(2L).getPrincipal(), null);
        interceptor.onSessionConnected(new SessionConnectedEvent(this, connect, anonymous));
        Message<byte[]> message = message(SimpMessageType.MESSAGE, "/topic/projects/10/resync", "receiver-session");

        // when
        Message<?> result = interceptor.beforeHandle(message, channel, handler);

        // then
        assertNull(result);
        verifyNoInteractions(projectAccessService);
    }

    @Test
    @DisplayName("권한 DB 조회에 실패해도 project 방송을 허용하지 않는다")
    void beforeHandle_AccessLookupFails_BlocksMessage() {
        // given
        connected("receiver-session", 2L);
        Message<byte[]> message = message(SimpMessageType.MESSAGE, "/topic/projects/10/resync", "receiver-session");
        doThrow(new DataAccessResourceFailureException("test database unavailable"))
                .when(projectAccessService).requireReadAccess(10L, 2L);

        // when
        Message<?> result = interceptor.beforeHandle(message, channel, handler);

        // then
        assertNull(result);
    }

    @Test
    @DisplayName("한 프로젝트 권한을 잃어도 같은 session의 다른 프로젝트 메시지는 허용한다")
    void beforeHandle_OnlyOneProjectRevoked_PreservesOtherProject() {
        // given
        connected("receiver-session", 2L);
        Message<byte[]> revoked = message(SimpMessageType.MESSAGE, "/topic/projects/10/resync", "receiver-session");
        Message<byte[]> allowed = message(SimpMessageType.MESSAGE, "/topic/projects/20/resync", "receiver-session");
        doThrow(new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).requireReadAccess(10L, 2L);

        // when
        Message<?> revokedResult = interceptor.beforeHandle(revoked, channel, handler);
        Message<?> allowedResult = interceptor.beforeHandle(allowed, channel, handler);

        // then
        assertNull(revokedResult);
        assertSame(allowed, allowedResult);
        verify(projectAccessService).requireReadAccess(20L, 2L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    @DisplayName("session의 access token이 만료됐으면 project 방송을 차단한다")
    void beforeHandle_TokenExpired_BlocksMessage(long secondsPastExpiry) {
        // given
        connected("receiver-session", 2L, TOKEN, NOW.minusSeconds(secondsPastExpiry));
        Message<byte[]> message = message(SimpMessageType.MESSAGE, "/topic/projects/10/operations", "receiver-session");

        // when
        Message<?> result = interceptor.beforeHandle(message, channel, handler);

        // then
        assertNull(result);
        verifyNoInteractions(redisUtil, projectAccessService);
    }

    @Test
    @DisplayName("session의 access token이 blacklist에 있으면 읽기 권한이 있어도 project 방송을 차단한다")
    void beforeHandle_TokenBlacklisted_BlocksMessage() {
        // given
        connected("receiver-session", 2L);
        Message<byte[]> message = message(SimpMessageType.MESSAGE, "/topic/projects/10/resync", "receiver-session");
        when(redisUtil.isBlackList(TOKEN)).thenReturn(true);

        // when
        Message<?> result = interceptor.beforeHandle(message, channel, handler);

        // then
        assertNull(result);
        verifyNoInteractions(projectAccessService);
    }

    @Test
    @DisplayName("한 token이 로그아웃돼도 같은 회원의 다른 token session은 방송을 계속 받는다")
    void beforeHandle_OneTokenBlacklisted_PreservesOtherSessionOfSameMember() {
        // given
        connected("logged-out-session", 2L, "token-a", NOW.plusSeconds(3600));
        connected("other-device-session", 2L, "token-b", NOW.plusSeconds(3600));
        Message<byte[]> loggedOut = message(SimpMessageType.MESSAGE, "/topic/projects/10/operations", "logged-out-session");
        Message<byte[]> otherDevice = message(SimpMessageType.MESSAGE, "/topic/projects/10/operations", "other-device-session");
        when(redisUtil.isBlackList("token-a")).thenReturn(true);

        // when
        Message<?> loggedOutResult = interceptor.beforeHandle(loggedOut, channel, handler);
        Message<?> otherDeviceResult = interceptor.beforeHandle(otherDevice, channel, handler);

        // then
        assertNull(loggedOutResult);
        assertSame(otherDevice, otherDeviceResult);
        verify(projectAccessService).requireReadAccess(10L, 2L);
    }

    @Test
    @DisplayName("blacklist 조회에 실패해도 project 방송을 허용하지 않는다")
    void beforeHandle_BlacklistLookupFails_BlocksMessage() {
        // given
        connected("receiver-session", 2L);
        Message<byte[]> message = message(SimpMessageType.MESSAGE, "/topic/projects/10/resync", "receiver-session");
        when(redisUtil.isBlackList(TOKEN)).thenThrow(new DataAccessResourceFailureException("test redis unavailable"));

        // when
        Message<?> result = interceptor.beforeHandle(message, channel, handler);

        // then
        assertNull(result);
        verifyNoInteractions(projectAccessService);
    }

    @Test
    @DisplayName("token 만료 시각을 확인할 수 없으면 session을 등록하지 않아 방송을 차단한다")
    void beforeHandle_ExpirationUnreadable_BlocksMessage() {
        // given
        when(jwtUtil.getClaims(TOKEN)).thenThrow(new AuthException(AuthErrorCode.TOKEN_INVALID));
        interceptor.onSessionConnected(new SessionConnectedEvent(this,
                message(SimpMessageType.CONNECT_ACK, null, "receiver-session"), authentication(2L)));
        Message<byte[]> message = message(SimpMessageType.MESSAGE, "/topic/projects/10/resync", "receiver-session");

        // when
        Message<?> result = interceptor.beforeHandle(message, channel, handler);

        // then
        assertNull(result);
        verifyNoInteractions(redisUtil, projectAccessService);
    }

    @Test
    @DisplayName("인증 객체에 token이 없으면 session을 등록하지 않아 방송을 차단한다")
    void beforeHandle_CredentialsMissing_BlocksMessage() {
        // given
        Authentication withoutToken = authentication(2L, null);
        interceptor.onSessionConnected(new SessionConnectedEvent(this,
                message(SimpMessageType.CONNECT_ACK, null, "receiver-session"), withoutToken));
        Message<byte[]> message = message(SimpMessageType.MESSAGE, "/topic/projects/10/resync", "receiver-session");

        // when
        Message<?> result = interceptor.beforeHandle(message, channel, handler);

        // then
        assertNull(result);
        verifyNoInteractions(redisUtil, projectAccessService);
    }

    @ParameterizedTest
    @EnumSource(value = SimpMessageType.class, names = {"CONNECT_ACK", "DISCONNECT_ACK", "HEARTBEAT"})
    @DisplayName("연결·종료·heartbeat frame에는 project 권한 검사를 적용하지 않는다")
    void beforeHandle_ControlFrame_PassesThrough(SimpMessageType type) {
        // given
        Message<byte[]> message = message(type, null, "receiver-session");

        // when
        Message<?> result = interceptor.beforeHandle(message, channel, handler);

        // then
        assertSame(message, result);
        verifyNoInteractions(projectAccessService);
    }

    @Test
    @DisplayName("쓰기 거부를 알리는 사용자 전용 오류 큐는 project 방송 차단 대상에서 제외한다")
    void beforeHandle_PrivateErrorResult_PassesThrough() {
        // given
        Message<byte[]> message = message(SimpMessageType.MESSAGE,
                "/queue/projects/10/operation-results-userreceiver-session", "receiver-session");

        // when
        Message<?> result = interceptor.beforeHandle(message, channel, handler);

        // then
        assertSame(message, result);
        verifyNoInteractions(projectAccessService);
    }

    @Test
    @DisplayName("project ID가 Long 범위를 벗어나면 방송을 차단한다")
    void beforeHandle_InvalidProjectId_BlocksMessage() {
        // given
        connected("receiver-session", 2L);
        Message<byte[]> message = message(SimpMessageType.MESSAGE,
                "/topic/projects/99999999999999999999/resync", "receiver-session");

        // when
        Message<?> result = interceptor.beforeHandle(message, channel, handler);

        // then
        assertNull(result);
        verifyNoInteractions(projectAccessService);
    }

    private void connected(String sessionId, Long memberId) {
        connected(sessionId, memberId, TOKEN, NOW.plusSeconds(3600));
    }

    private void connected(String sessionId, Long memberId, String token, Instant expiresAt) {
        Claims claims = mock(Claims.class);
        when(claims.getExpiration()).thenReturn(Date.from(expiresAt));
        when(jwtUtil.getClaims(token)).thenReturn(claims);
        interceptor.onSessionConnected(new SessionConnectedEvent(this,
                message(SimpMessageType.CONNECT_ACK, null, sessionId), authentication(memberId, token)));
    }

    private Authentication authentication(Long memberId) {
        return authentication(memberId, TOKEN);
    }

    private Authentication authentication(Long memberId, String token) {
        CustomUserDetails details = new CustomUserDetails(MemberResDTO.MemberResultDTO.builder()
                .id(memberId).isActive(true).role(Role.ROLE_USER).build());
        return new UsernamePasswordAuthenticationToken(details, token, details.getAuthorities());
    }

    private Message<byte[]> message(SimpMessageType type, String destination, String sessionId) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(type);
        headers.setSessionId(sessionId);
        headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
