package com.ai_kids_care.v1.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * RRN 단방향 해시 유틸리티 — ADR-0024 D1.
 *
 * <p>HMAC-SHA-256(key = pepper, message = rrnFirst6 ‖ rrnBack7) 를 Base64URL-nopad 으로 인코딩한다.
 * 순수 JDK 표준 라이브러리만 사용하며 Spring Bean 이 아니다.
 * pepper 가 비어 있거나 null 이면 즉시 {@link IllegalArgumentException} 을 던진다(fail-fast).
 */
public final class RrnHashUtil {

    private static final String ALGORITHM = "HmacSHA256";

    private RrnHashUtil() {
        // utility class — no instantiation
    }

    /**
     * RRN 의 HMAC-SHA-256 해시를 Base64URL-nopad 문자열로 반환한다.
     *
     * @param pepper   HMAC 키로 사용할 pepper 값 (null 또는 빈 문자열 불허)
     * @param rrnFirst6 주민등록번호 앞 6자리 (생년월일)
     * @param rrnBack7  주민등록번호 뒷 7자리
     * @return Base64URL-nopad 인코딩된 HMAC-SHA-256 해시
     * @throws IllegalArgumentException pepper 가 null 이거나 공백인 경우
     */
    public static String hash(String pepper, String rrnFirst6, String rrnBack7) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalArgumentException("RRN hash pepper must not be null or blank");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    pepper.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(
                    (rrnFirst6 + rrnBack7).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA256 algorithm unavailable", e);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("Invalid HMAC key", e);
        }
    }
}
