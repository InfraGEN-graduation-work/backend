package com.infragen.infragen.global.auth.websocket;

import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import com.infragen.infragen.global.auth.CustomUserDetails;
import com.infragen.infragen.global.util.JwtUtil;
import com.infragen.infragen.global.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 단일 인스턴스의 수신 session을 식별하고 project 방송 전달 직전에 access token 유효성과 현재 읽기 권한을 확인한다.
 *
 * <p>inbound 재검증은 SUBSCRIBE, SEND 때만 일어나 보기만 하는 연결에는 닿지 않으므로, 만료와 로그아웃은 수신 시점에 이 클래스가 확인한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompProjectOutboundInterceptor implements ExecutorChannelInterceptor {
    private static final Pattern PROJECT_TOPIC = Pattern.compile("^/topic/projects/(\\d+)/(operations|resync)$");

    private final ProjectAccessService projectAccessService;
    private final RedisUtil redisUtil;
    private final JwtUtil jwtUtil;
    private final Clock clock;
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    /**
     * 인증된 CONNECT 완료 시 session과 회원, 연결에 쓴 access token과 만료 시각을 연결한다. project 권한은 보관하지 않는다.
     *
     * <p>token이나 만료 시각을 확인할 수 없으면 session을 등록하지 않아 해당 session의 방송은 차단된다.
     */
    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (sessionId != null && event.getUser() instanceof Authentication authentication
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails
                && userDetails.isEnabled() && userDetails.getMemberId() != null
                && authentication.getCredentials() instanceof String token) {
            Instant expiresAt = expirationOf(token);
            if (expiresAt != null) {
                sessions.put(sessionId, new SessionInfo(userDetails.getMemberId(), token, expiresAt));
            }
        }
    }

    /** 중복 disconnect 이벤트에도 안전하게 session 식별 정보를 제거한다. */
    @EventListener
    public void onSessionDisconnected(SessionDisconnectEvent event) {
        sessions.remove(event.getSessionId());
    }

    /**
     * outbound 대기열을 통과한 메시지를 실제 전달하기 전에 수신 session의 access token 만료, blacklist 여부와 수신자의 읽기 권한으로 검증한다.
     * 검증에 실패하거나 확인할 수 없으면 해당 메시지만 차단하고 연결과 다른 session, 다른 project 구독은 유지한다.
     */
    @Override
    public @Nullable Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) {
            return message;
        }
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        if (destination == null) {
            return message;
        }
        Matcher matcher = PROJECT_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }

        // broker가 설정한 수신 session을 사용한다. 원본 방송의 simpUser는 발신자일 수 있다.
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        SessionInfo session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        Long projectId;
        try {
            projectId = Long.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }

        // 연결 때 계산한 만료 시각과 비교만 하므로 메시지마다 JWT를 다시 파싱하지 않는다.
        if (!clock.instant().isBefore(session.expiresAt())) {
            return null;
        }

        try {
            // 로그아웃은 요청에 쓴 token 하나만 blacklist하므로 같은 회원의 다른 token session은 영향이 없다.
            if (redisUtil.isBlackList(session.token())) {
                return null;
            }
            projectAccessService.requireReadAccess(projectId, session.memberId());
            return message;
        } catch (ProjectException exception) {
            return null;
        } catch (DataAccessException exception) {
            log.warn("프로젝트 방송 수신 검증 조회 실패: projectId={}, sessionId={}", projectId, sessionId, exception);
            return null;
        }
    }

    private Instant expirationOf(String token) {
        try {
            return jwtUtil.getClaims(token).getExpiration().toInstant();
        } catch (RuntimeException exception) {
            log.warn("STOMP session token 만료 시각 확인 실패");
            return null;
        }
    }

    private record SessionInfo(Long memberId, String token, Instant expiresAt) {
    }
}
