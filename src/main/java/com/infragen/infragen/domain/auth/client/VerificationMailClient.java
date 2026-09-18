package com.infragen.infragen.domain.auth.client;

import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class VerificationMailClient {
    private final JavaMailSender mailSender;
    private final String from;

    public VerificationMailClient(JavaMailSender mailSender,
            @Value("${MAIL_FROM:${spring.mail.username:}}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendCode(String email, String code) {
        if (from == null || from.isBlank()) {
            throw new AuthException(AuthErrorCode.EMAIL_SEND_FAILED);
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("[InfraGen] 회원가입 이메일 인증번호");
        message.setText("회원가입 인증번호는 " + code + " 입니다.\n5분 이내에 입력해주세요.\n"
                + "본인이 요청하지 않았다면 이 메일을 무시해주세요.");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new AuthException(AuthErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
