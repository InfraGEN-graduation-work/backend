package com.infragen.infragen.global.auth.websocket;

import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import com.infragen.infragen.global.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {
    private static final String SESSION_ID = "session-a";
    private static final String TOKEN = "access-token";

    @Mock
    private StompAccessTokenAuthenticator tokenAuthenticator;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private MessageChannel channel;

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(tokenAuthenticator, projectAccessService);
        when(tokenAuthenticator.authenticate(TOKEN)).thenReturn(authentication(2L));
        interceptor.preSend(connect(), channel);
    }

    @Test
    @DisplayName("cursors 구독은 현재 읽기 권한을 확인하고 허용한다")
    void preSend_subscribeCursorTopic_checksReadAccess() {
        // given
        Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, "/topic/projects/10/cursors");

        // when
        Message<?> result = interceptor.preSend(subscribe, channel);

        // then
        verify(projectAccessService).requireReadAccess(10L, 2L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("읽기 권한이 없으면 cursors 구독을 거부한다")
    void preSend_subscribeCursorTopicWithoutAccess_throws() {
        // given
        Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, "/topic/projects/10/cursors");
        doThrow(new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).requireReadAccess(10L, 2L);

        // when
        ProjectException exception = assertThrows(ProjectException.class, () -> interceptor.preSend(subscribe, channel));

        // then
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, exception.getCode());
    }

    @Test
    @DisplayName("cursors SEND는 허용하되 권한은 Service에서 확인하므로 interceptor가 조회하지 않는다")
    void preSend_sendCursor_allowsWithoutAccessLookup() {
        // given
        Message<byte[]> send = frame(StompCommand.SEND, "/app/projects/10/cursors");

        // when
        Message<?> result = assertDoesNotThrow(() -> interceptor.preSend(send, channel));

        // then
        assertNotNull(result);
        verifyNoInteractions(projectAccessService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/app/projects/10/cursors/extra", "/app/projects/abc/cursors", "/app/projects/10/cursor"})
    @DisplayName("cursors 형식이 아닌 SEND destination은 거부한다")
    void preSend_sendMalformedCursorDestination_throws(String destination) {
        // given
        Message<byte[]> send = frame(StompCommand.SEND, destination);

        // when
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(send, channel));

        // then
        assertEquals("지원하지 않는 STOMP destination입니다.", exception.getMessage());
    }

    @Test
    @DisplayName("cursors topic으로 직접 SEND하는 것은 거부한다")
    void preSend_sendToCursorTopic_throws() {
        // given
        Message<byte[]> send = frame(StompCommand.SEND, "/topic/projects/10/cursors");

        // when
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(send, channel));

        // then
        assertEquals("지원하지 않는 STOMP destination입니다.", exception.getMessage());
    }

    @Test
    @DisplayName("기존 operations SEND와 operations 구독의 허용은 그대로 유지한다")
    void preSend_existingOperationDestinations_stillAllowed() {
        // given
        Message<byte[]> send = frame(StompCommand.SEND, "/app/projects/10/operations");
        Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, "/topic/projects/10/operations");

        // when
        Message<?> sent = interceptor.preSend(send, channel);
        Message<?> subscribed = interceptor.preSend(subscribe, channel);

        // then
        assertNotNull(sent);
        assertNotNull(subscribed);
        verify(projectAccessService).requireReadAccess(10L, 2L);
    }

    private Message<byte[]> connect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(SESSION_ID);
        accessor.setNativeHeader("Authorization", "Bearer " + TOKEN);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> frame(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(SESSION_ID);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Authentication authentication(Long memberId) {
        CustomUserDetails details = new CustomUserDetails(MemberResDTO.MemberResultDTO.builder()
                .id(memberId).isActive(true).role(Role.ROLE_USER).build());
        return new UsernamePasswordAuthenticationToken(details, TOKEN, details.getAuthorities());
    }
}
