package com.infragen.infragen.global.auth.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import com.infragen.infragen.global.auth.CustomUserDetails;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import lombok.RequiredArgsConstructor;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * STOMP CONNECT frame의 access token을 검증하고 이후 message의 Principal을 설정한다.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern OPERATIONS_DESTINATION =
            Pattern.compile("^/app/projects/(\\d+)/operations$");
    private static final Pattern OPERATIONS_TOPIC_DESTINATION =
            Pattern.compile("^/topic/projects/(\\d+)/operations$");
    private static final Pattern ROOM_RESYNC_DESTINATION =
            Pattern.compile("^/topic/projects/(\\d+)/resync$");
    private static final Pattern OPERATION_RESULT_DESTINATION =
            Pattern.compile("^/user/queue/projects/(\\d+)/operation-results$");

    private final StompAccessTokenAuthenticator tokenAuthenticator;
    private final ProjectAccessService projectAccessService;
    private final Map<String, Authentication> sessionAuthentications = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Authentication authentication = authenticate(accessor);
            sessionAuthentications.put(accessor.getSessionId(), authentication);
            accessor.setUser(authentication);
            return rebuildMessage(message, accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                || StompCommand.SEND.equals(accessor.getCommand())) {
            validateMessage(accessor);
            return rebuildMessage(message, accessor);
        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            sessionAuthentications.remove(accessor.getSessionId());
        }

        return message;
    }

    private Message<?> rebuildMessage(Message<?> message, StompHeaderAccessor accessor) {
        return MessageBuilder.withPayload(message.getPayload())
                .setHeaders(accessor)
                .build();
    }

    private void validateMessage(StompHeaderAccessor accessor) {
        Authentication authentication = getAuthentication(accessor);
        if (authentication == null) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }

        String destination = accessor.getDestination();
        Authentication refreshedAuthentication = tokenAuthenticator.authenticate(accessToken(authentication));
        sessionAuthentications.put(accessor.getSessionId(), refreshedAuthentication);
        accessor.setUser(refreshedAuthentication);
        boolean allowedDestination = switch (accessor.getCommand()) {
            case SEND -> matches(OPERATIONS_DESTINATION, destination);
            case SUBSCRIBE -> matches(OPERATIONS_TOPIC_DESTINATION, destination)
                    || matches(ROOM_RESYNC_DESTINATION, destination)
                    || matches(OPERATION_RESULT_DESTINATION, destination);
            default -> false;
        };

        if (!allowedDestination) {
            throw new IllegalArgumentException("지원하지 않는 STOMP destination입니다.");
        }

        Long projectId = extractProjectId(destination);
        CustomUserDetails userDetails = getUserDetails(accessor);

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            projectAccessService.requireWriteAccess(projectId, userDetails.getMemberId());
        } else {
            projectAccessService.requireReadAccess(projectId, userDetails.getMemberId());
        }
    }

    private boolean matches(Pattern pattern, String destination) {
        return destination != null && pattern.matcher(destination).matches();
    }

    private Long extractProjectId(String destination) {
        Pattern pattern = matches(OPERATIONS_DESTINATION, destination)
                ? OPERATIONS_DESTINATION
                : matches(OPERATIONS_TOPIC_DESTINATION, destination)
                        ? OPERATIONS_TOPIC_DESTINATION
                        : matches(ROOM_RESYNC_DESTINATION, destination)
                                ? ROOM_RESYNC_DESTINATION
                                : OPERATION_RESULT_DESTINATION;
        Matcher matcher = pattern.matcher(destination);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("지원하지 않는 STOMP destination입니다.");
        }
        return Long.valueOf(matcher.group(1));
    }

    private CustomUserDetails getUserDetails(StompHeaderAccessor accessor) {
        Object user = accessor.getUser();
        if (user instanceof Authentication authentication
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        if (user instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        throw new AuthException(AuthErrorCode.TOKEN_INVALID);
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }

        return tokenAuthenticator.authenticate(token);
    }

    private Authentication getAuthentication(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication authentication) {
            return authentication;
        }
        return sessionAuthentications.get(accessor.getSessionId());
    }

    private String accessToken(Authentication authentication) {
        if (authentication.getCredentials() instanceof String token && !token.isBlank()) {
            return token;
        }
        throw new AuthException(AuthErrorCode.TOKEN_INVALID);
    }
}
