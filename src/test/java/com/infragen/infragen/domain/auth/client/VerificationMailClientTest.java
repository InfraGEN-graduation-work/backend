package com.infragen.infragen.domain.auth.client;

import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationMailClientTest {
    @Mock private JavaMailSender sender;

    @Test
    void sendCode_ValidConfiguration_SendsCodeToRequestedAddress() {
        // given
        var client = new VerificationMailClient(sender, "noreply@example.com");
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);

        // when
        client.sendCode("user@example.com", "012345");

        // then
        verify(sender).send(message.capture());
        assertEquals("noreply@example.com", message.getValue().getFrom());
        assertArrayEquals(new String[]{"user@example.com"}, message.getValue().getTo());
        assertTrue(message.getValue().getText().contains("012345"));
    }

    @Test
    void sendCode_SmtpFailure_ReturnsDomainError() {
        // given
        var client = new VerificationMailClient(sender, "noreply@example.com");
        doThrow(new MailSendException("delivery failed")).when(sender).send(any(SimpleMailMessage.class));

        // when
        AuthException error = assertThrows(AuthException.class, () -> client.sendCode("user@example.com", "123456"));

        // then
        assertEquals(AuthErrorCode.EMAIL_SEND_FAILED, error.getCode());
    }

    @Test
    void sendCode_MissingSender_DoesNotSend() {
        // given
        var client = new VerificationMailClient(sender, "");

        // when
        AuthException error = assertThrows(AuthException.class, () -> client.sendCode("user@example.com", "123456"));

        // then
        assertEquals(AuthErrorCode.EMAIL_SEND_FAILED, error.getCode());
        verifyNoInteractions(sender);
    }
}
