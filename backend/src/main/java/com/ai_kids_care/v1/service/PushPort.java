package com.ai_kids_care.v1.service;

/**
 * Delivery seam for PUSH notifications. Keeps {@code NotificationService}/{@code StaffAlertService}
 * independent of the concrete PUSH provider (Pushover): callers depend on this port and the
 * dispatcher records FAILED on a thrown {@link IllegalStateException}, mirroring the {@link SmsPort}
 * seam. Swapping the provider only changes the adapter, and tests mock this port instead of hitting
 * a real gateway.
 *
 * <p>The return type is this port's own {@link PushDeliveryStatus}, not the Pushover SDK's
 * {@code net.pushover.client.Status} — the port never leaks the SDK type (ARC-01).
 */
public interface PushPort {

    /**
     * Send a PUSH message to a single recipient identified by their provider user key.
     *
     * @param userKey recipient's provider user key (from {@code push_subscriptions.address})
     * @throws IllegalStateException    when the provider reports a delivery failure
     * @throws IllegalArgumentException when {@code userKey} is blank (programming error, not a delivery failure)
     */
    PushDeliveryStatus sendToUser(String userKey, String title, String message);

    /**
     * Provider-agnostic delivery status returned by {@link #sendToUser}. Mirrors the fields of the
     * Pushover SDK's {@code net.pushover.client.Status} ({@code status}, {@code requestId}) without
     * exposing that SDK type past the adapter.
     */
    record PushDeliveryStatus(int status, String requestId) {
    }
}
