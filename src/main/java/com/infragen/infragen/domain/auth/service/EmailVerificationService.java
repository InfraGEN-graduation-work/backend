package com.infragen.infragen.domain.auth.service;

import com.infragen.infragen.domain.auth.client.VerificationMailClient;
import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import com.infragen.infragen.domain.auth.repository.EmailVerificationRepository;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.global.properties.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationService {
    private final EmailVerificationRepository repository;
    private final VerificationMailClient mailClient;
    private final MemberRepository memberRepository;
    private final SecretKeySpec signingKey;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(EmailVerificationRepository repository,
            VerificationMailClient mailClient, MemberRepository memberRepository, JwtProperties properties) {
        this.repository = repository;
        this.mailClient = mailClient;
        this.memberRepository = memberRepository;
        this.signingKey = new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public void sendCode(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new MemberException(MemberErrorCode.DUPLICATE_EMAIL);
        }
        String emailKey = digest("email:" + canonicalEmail(email));
        String requestId = UUID.randomUUID().toString();
        if (!repository.reserveSend(emailKey, requestId)) {
            throw new AuthException(AuthErrorCode.EMAIL_CODE_RATE_LIMITED);
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        mailClient.sendCode(email, code);
        if (!repository.storeCode(emailKey, requestId, codeDigest(email, code))) {
            throw new AuthException(AuthErrorCode.EMAIL_SEND_FAILED);
        }
    }

    public void verifyAndConsume(String email, String code) {
        if (email == null || code == null || !code.matches("[0-9]{6}")) {
            throw new AuthException(AuthErrorCode.EMAIL_CODE_INVALID);
        }
        long result = repository.consumeCode(digest("email:" + canonicalEmail(email)), codeDigest(email, code));
        if (result == -1) {
            throw new AuthException(AuthErrorCode.EMAIL_CODE_RATE_LIMITED);
        }
        if (result != 1) {
            throw new AuthException(AuthErrorCode.EMAIL_CODE_INVALID);
        }
    }

    private String canonicalEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    private String codeDigest(String email, String code) {
        return digest("code:" + canonicalEmail(email) + ":" + code);
    }

    private String digest(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            return HexFormat.of().formatHex(mac.doFinal(("signup-email:" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("이메일 인증 서명을 생성하지 못했습니다.", e);
        }
    }
}
