package com.infragen.infragen.global.auth.websocket;

import java.nio.charset.StandardCharsets;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import com.infragen.infragen.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * STOMP client message 처리 중 발생한 예외를 안전한 ERROR frame으로 변환한다.
 */
@Component
@RequiredArgsConstructor
public class StompErrorHandler extends StompSubProtocolErrorHandler {
    private static final String INVALID_REQUEST_CODE = "STOMP400_1";
    private static final String INVALID_REQUEST_MESSAGE = "유효하지 않은 STOMP 요청입니다.";
    private static final String INTERNAL_ERROR_CODE = "STOMP500_1";
    private static final String INTERNAL_ERROR_MESSAGE = "STOMP 요청을 처리할 수 없습니다.";

    private final ObjectMapper objectMapper;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(
            Message<byte[]> clientMessage,
            Throwable exception
    ) {
        ErrorPayload errorPayload = resolveErrorPayload(exception);
        StompHeaderAccessor errorAccessor = StompHeaderAccessor.create(StompCommand.ERROR);
        errorAccessor.setMessage("STOMP message processing failed");
        errorAccessor.setContentType(MimeTypeUtils.APPLICATION_JSON);

        byte[] payload = serialize(errorPayload);
        return MessageBuilder.createMessage(payload, errorAccessor.getMessageHeaders());
    }

    private ErrorPayload resolveErrorPayload(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof GeneralException generalException) {
                return new ErrorPayload(
                        generalException.getCode().getCode(),
                        generalException.getCode().getMessage()
                );
            }
            if (current instanceof IllegalArgumentException) {
                return new ErrorPayload(INVALID_REQUEST_CODE, INVALID_REQUEST_MESSAGE);
            }
            current = current.getCause();
        }

        return new ErrorPayload(INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE);
    }

    private byte[] serialize(ErrorPayload errorPayload) {
        try {
            return objectMapper.writeValueAsString(errorPayload).getBytes(StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return ("{\"code\":\"" + INTERNAL_ERROR_CODE
                    + "\",\"message\":\"" + INTERNAL_ERROR_MESSAGE + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    private record ErrorPayload(String code, String message) {
    }
}
