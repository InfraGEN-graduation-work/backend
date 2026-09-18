package com.infragen.infragen.domain.auth.service;

import com.infragen.infragen.domain.auth.client.VerificationMailClient;
import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import com.infragen.infragen.domain.auth.repository.EmailVerificationRepository;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.global.properties.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {
    @Mock private EmailVerificationRepository repository;
    @Mock private VerificationMailClient mailClient;
    @Mock private MemberRepository memberRepository;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-only-email-verification-secret");
        service = new EmailVerificationService(repository, mailClient, memberRepository, properties);
    }

    @Test
    void sendCode_NewEmail_SendsSixDigitsAndStoresDigestAfterDelivery() {
        // given
        when(repository.reserveSend(anyString(), anyString())).thenReturn(true);
        when(repository.storeCode(anyString(), anyString(), anyString())).thenReturn(true);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);

        // when
        service.sendCode("user@example.com");

        // then
        var order = inOrder(repository, mailClient);
        order.verify(repository).reserveSend(anyString(), anyString());
        order.verify(mailClient).sendCode(eq("user@example.com"), code.capture());
        order.verify(repository).storeCode(anyString(), anyString(), digest.capture());
        assertTrue(code.getValue().matches("[0-9]{6}"));
        assertTrue(digest.getValue().matches("[0-9a-f]{64}"));
        assertNotEquals(code.getValue(), digest.getValue());
    }

    @Test
    void sendCode_DuplicateEmail_DoesNotSend() {
        // given
        when(memberRepository.existsByEmail("user@example.com")).thenReturn(true);

        // when
        MemberException error = assertThrows(MemberException.class, () -> service.sendCode("user@example.com"));

        // then
        assertEquals(MemberErrorCode.DUPLICATE_EMAIL, error.getCode());
        verifyNoInteractions(repository, mailClient);
    }

    @Test
    void sendCode_RateLimited_DoesNotSend() {
        // given
        when(repository.reserveSend(anyString(), anyString())).thenReturn(false);

        // when
        AuthException error = assertThrows(AuthException.class, () -> service.sendCode("user@example.com"));

        // then
        assertEquals(AuthErrorCode.EMAIL_CODE_RATE_LIMITED, error.getCode());
        verifyNoInteractions(mailClient);
    }

    @Test
    void sendCode_MailFailure_DoesNotStoreUsableCode() {
        // given
        when(repository.reserveSend(anyString(), anyString())).thenReturn(true);
        doThrow(new AuthException(AuthErrorCode.EMAIL_SEND_FAILED)).when(mailClient).sendCode(anyString(), anyString());

        // when
        AuthException error = assertThrows(AuthException.class, () -> service.sendCode("user@example.com"));

        // then
        assertEquals(AuthErrorCode.EMAIL_SEND_FAILED, error.getCode());
        verify(repository, never()).storeCode(anyString(), anyString(), anyString());
    }

    @Test
    void sendCode_ReservationExpired_ReturnsFailure() {
        // given
        when(repository.reserveSend(anyString(), anyString())).thenReturn(true);
        when(repository.storeCode(anyString(), anyString(), anyString())).thenReturn(false);

        // when
        AuthException error = assertThrows(AuthException.class, () -> service.sendCode("user@example.com"));

        // then
        assertEquals(AuthErrorCode.EMAIL_SEND_FAILED, error.getCode());
    }

    @Test
    void verifyAndConsume_DeliveredCode_UsesSameEmailBoundDigest() {
        // given
        when(repository.reserveSend(anyString(), anyString())).thenReturn(true);
        when(repository.storeCode(anyString(), anyString(), anyString())).thenReturn(true);
        service.sendCode("User@example.com");
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        verify(mailClient).sendCode(eq("User@example.com"), code.capture());
        verify(repository).storeCode(key.capture(), anyString(), digest.capture());
        when(repository.consumeCode(key.getValue(), digest.getValue())).thenReturn(1L);

        // when
        service.verifyAndConsume("user@example.com", code.getValue());

        // then
        verify(repository).consumeCode(key.getValue(), digest.getValue());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void verifyAndConsume_RejectedByStore_ThrowsDomainError(long result) {
        // given
        when(repository.consumeCode(anyString(), anyString())).thenReturn(result);

        // when
        AuthException error = assertThrows(AuthException.class,
                () -> service.verifyAndConsume("user@example.com", "012345"));

        // then
        assertEquals(result == -1 ? AuthErrorCode.EMAIL_CODE_RATE_LIMITED : AuthErrorCode.EMAIL_CODE_INVALID,
                error.getCode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345", "1234567", "abcdef", "１２３４５６"})
    void verifyAndConsume_MalformedCode_DoesNotAccessStore(String code) {
        // given
        String email = "user@example.com";

        // when
        AuthException error = assertThrows(AuthException.class, () -> service.verifyAndConsume(email, code));

        // then
        assertEquals(AuthErrorCode.EMAIL_CODE_INVALID, error.getCode());
        verifyNoInteractions(repository);
    }
}
