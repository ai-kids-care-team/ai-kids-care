package com.ai_kids_care.v1.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public final class AesGcmCryptoUtil {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE_BYTES = 32;
    private static final int GCM_IV_SIZE_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesGcmCryptoUtil() {
    }

    public static EncryptedPayload encrypt(String plainText, String base64Key) {
        try {
            byte[] iv = new byte[GCM_IV_SIZE_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(decodeKey(base64Key), "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return new EncryptedPayload(Base64.getEncoder().encodeToString(encrypted), Base64.getEncoder().encodeToString(iv));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt value", e);
        }
    }

    public static String decrypt(String base64CipherText, String base64Iv, String base64Key) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(decodeKey(base64Key), "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.getDecoder().decode(base64Iv))
            );

            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(base64CipherText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt value", e);
        }
    }

    private static byte[] decodeKey(String base64Key) {
        byte[] key = Base64.getDecoder().decode(base64Key);
        if (key.length != AES_KEY_SIZE_BYTES) {
            throw new IllegalArgumentException("AES-GCM key must be a 32-byte Base64 value");
        }
        return key;
    }

    public record EncryptedPayload(String ciphertext, String iv) {
    }
}
