package com.ai_kids_care.v1.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Shared SHA-256 hex digest helper (QLT-02: converges three previously-duplicated private
 * implementations in {@code LoginThrottleService}, {@code PasswordManagementService}, and
 * {@code PasswordResetTokenService}). Deliberately does <b>no</b> normalization (trim/lowercase)
 * here — callers that need it (e.g. identifier hashing) must apply it explicitly before calling,
 * so normalization semantics stay visible at the call site instead of hidden in a shared utility.
 */
public final class HashingUtils {

    private HashingUtils() {
    }

    /** SHA-256 of {@code input} (UTF-8 bytes), as lowercase hex. No trim/lowercase normalization. */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is present on every JVM — unreachable.
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
