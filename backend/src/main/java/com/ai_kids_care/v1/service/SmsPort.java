package com.ai_kids_care.v1.service;

/**
 * Delivery seam for SMS notifications. Keeps {@code NotificationService}/{@code StaffAlertService}
 * independent of the concrete SMS provider (Solapi): callers depend on this port and the dispatcher
 * records FAILED on a thrown {@link IllegalStateException} — the same seam {@link PushPort} provides
 * for PUSH delivery. Swapping the provider only changes the adapter, and tests mock this port instead
 * of hitting a real gateway.
 */
public interface SmsPort {

    /**
     * Send an SMS to a single recipient phone number.
     *
     * @param phone recipient phone (from {@code users.phone})
     * @param text  message body
     * @throws IllegalStateException    when the provider reports a delivery failure
     * @throws IllegalArgumentException when {@code phone} is blank (programming error, not a delivery failure)
     */
    void send(String phone, String text);
}
