package com.ai_kids_care.v1.service;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * {@link EmailPort} implementation backed by SMTP via the container-auto-configured
 * {@link JavaMailSender} bean ({@code spring-boot-starter-mail}, populated from
 * {@code spring.mail.*} — see {@code com.ai_kids_care.v1.config.SmtpConfig} for the fail-fast
 * credential gate on the same properties). Sends a plain-text message; never logs the recipient
 * address, credentials, or body. Mirrors {@link SolapiSmsAdapter} (the {@link SmsPort}
 * implementation): translate the mail library's failure into an {@link IllegalStateException} the
 * dispatcher records as FAILED.
 */
@Service
public class SmtpEmailAdapter implements EmailPort {

    private final JavaMailSender mailSender;

    public SmtpEmailAdapter(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(String toAddress, String subject, String body) {
        if (!StringUtils.hasText(toAddress)) {
            throw new IllegalArgumentException("SmtpEmailAdapter: recipient email must not be blank");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toAddress);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // MailException (and its subtypes: MailSendException, MailAuthenticationException, ...)
            // never carry the message body/credentials in a way we forward here — only the
            // translated IllegalStateException's own static text propagates to the delivery record.
            throw new IllegalStateException("SMTP 이메일 발송 실패", e);
        }
    }
}
