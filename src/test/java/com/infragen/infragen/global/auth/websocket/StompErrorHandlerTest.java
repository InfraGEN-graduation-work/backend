package com.infragen.infragen.global.auth.websocket;

import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StompErrorHandlerTest {
    private final StompErrorHandler errorHandler = new StompErrorHandler(new ObjectMapper());

    @Test
    void handleGeneralException_returnsSanitizedErrorFrame() {
        // given
        AuthException exception = new AuthException(AuthErrorCode.TOKEN_INVALID);

        // when
        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(null, exception);

        // then
        String payload = new String(result.getPayload(), StandardCharsets.UTF_8);
        assertEquals(StompCommand.ERROR, StompHeaderAccessor.wrap(result).getCommand());
        assertTrue(payload.contains("AUTH401_1"));
        assertTrue(payload.contains("유효하지 않은 토큰입니다."));
    }

    @Test
    void handleIllegalArgumentException_doesNotExposeOriginalMessage() {
        // given
        IllegalArgumentException exception = new IllegalArgumentException("internal destination detail");

        // when
        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(null, exception);

        // then
        String payload = new String(result.getPayload(), StandardCharsets.UTF_8);
        assertTrue(payload.contains("STOMP400_1"));
        assertTrue(payload.contains("유효하지 않은 STOMP 요청입니다."));
        assertFalse(payload.contains("internal destination detail"));
    }
}
