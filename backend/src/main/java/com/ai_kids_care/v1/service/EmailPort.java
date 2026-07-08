package com.ai_kids_care.v1.service;

/**
 * Delivery seam for EMAIL notifications. Keeps {@code NotificationService} independent of the
 * concrete mail provider (SMTP via {@code JavaMailSender}): callers depend on this port and the
 * dispatcher records FAILED on a thrown {@link IllegalStateException} — the same seam
 * {@link PushPort}/{@link SmsPort} provide for their channels. Swapping the provider only changes
 * the adapter, and tests mock this port instead of hitting a real mail gateway.
 */
public interface EmailPort {

    /**
     * Send a plain-text email to a single recipient address.
     *
     * @param toAddress recipient email (from {@code users.email})
     * @param subject   message subject
     * @param body      message body (plain text)
     * @throws IllegalStateException    when the provider reports a delivery failure
     * @throws IllegalArgumentException when {@code toAddress} is blank (programming error, not a delivery failure)
     */
    void send(String toAddress, String subject, String body);
}
