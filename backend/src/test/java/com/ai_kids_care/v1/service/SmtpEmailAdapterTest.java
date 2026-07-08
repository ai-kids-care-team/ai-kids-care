package com.ai_kids_care.v1.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the SMTP delivery seam — no real SMTP calls (the {@code JavaMailSender} is
 * mocked). Verifies the adapter maps to/subject/body onto a {@link SimpleMailMessage}, guards a
 * blank recipient, and translates a mail-library failure into the {@link IllegalStateException}
 * the dispatcher records as FAILED. Mirrors {@code SolapiSmsAdapterTest}.
 */
class SmtpEmailAdapterTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SmtpEmailAdapter adapter = new SmtpEmailAdapter(mailSender);

    @Test
    void send_mapsToSubjectAndBody_andCallsSenderOnce() {
        adapter.send("guardian@example.com", "안전 알림", "hello world");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("guardian@example.com");
        assertThat(sent.getSubject()).isEqualTo("안전 알림");
        assertThat(sent.getText()).isEqualTo("hello world");
    }

    @Test
    void send_blankToAddress_throwsIllegalArgument_withoutCallingSender() {
        assertThatThrownBy(() -> adapter.send("   ", "subject", "body"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void send_mailException_translatedToIllegalState() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> adapter.send("guardian@example.com", "subject", "body"))
                .isInstanceOf(IllegalStateException.class);
    }
}
