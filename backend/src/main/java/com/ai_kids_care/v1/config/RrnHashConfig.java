package com.ai_kids_care.v1.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * RRN HMAC-SHA-256 pepper 설정 — ADR-0024 D5.
 *
 * <p>pepper 는 {@code rrn.hash.pepper} 속성에서 주입된다.
 * 값이 비어 있거나 없으면 Spring 컨텍스트 시작 시 즉시 실패한다(fail-fast).
 * 기본값은 허용되지 않는다.
 */
@Validated
@ConfigurationProperties(prefix = "rrn.hash")
public class RrnHashConfig {

    @NotBlank(message = "rrn.hash.pepper must not be blank — set RRN_HASH_PEPPER environment variable")
    private String pepper;

    public String getPepper() {
        return pepper;
    }

    public void setPepper(String pepper) {
        this.pepper = pepper;
    }
}
