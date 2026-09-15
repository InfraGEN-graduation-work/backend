package com.infragen.infragen.global.auth.websocket;

import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import com.infragen.infragen.global.auth.CustomUserDetails;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 단일 인스턴스의 수신 session을 식별하고 project 방송 전달 직전에 현재 읽기 권한을 확인한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompProjectOutboundInterceptor implements ExecutorChannelInterceptor {
    private static final Pattern PROJECT_TOPIC = Pattern.compile("^/topic/projects/(\\d+)/(operations|resync)$");

    private final ProjectAccessService projectAccessService;
    private final Map<String, Long> sessionMembers = new ConcurrentHashMap<>();

    /** 인증된 CONNECT 완료 시 session과 회원을 연결한다. token과 project 권한은 보관하지 않는다. */
    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (sessionId != null && event.getUser() instanceof Authentication authentication
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails
                && userDetails.isEnabled() && userDetails.getMemberId() != null) {
            sessionMembers.put(sessionId, userDetails.getMemberId());
        }
    }

    /** 중복 disconnect 이벤트에도 안전하게 session 식별 정보를 제거한다. */
    @EventListener
    public void onSessionDisconnected(SessionDisconnectEvent event) {
        sessionMembers.remove(event.getSessionId());
    }

    /**
     * outbound 대기열을 통과한 메시지를 실제 전달하기 전에 수신자의 읽기 권한으로 검증한다.
     * 권한이 없거나 확인할 수 없으면 해당 메시지만 차단하고 연결과 다른 project 구독은 유지한다.
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
        Long memberId = sessionId == null ? null : sessionMembers.get(sessionId);
        if (memberId == null) {
            return null;
        }
        Long projectId;
        try {
            projectId = Long.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }

        try {
            projectAccessService.requireReadAccess(projectId, memberId);
            return message;
        } catch (ProjectException exception) {
            return null;
        } catch (DataAccessException exception) {
            log.warn("프로젝트 방송 권한 조회 실패: projectId={}, sessionId={}", projectId, sessionId, exception);
            return null;
        }
    }
}
