package com.ai_kids_care.v1.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * D-STORE MinIO object-storage connection config. Values are injected via {@code ${MINIO_*}}
 * environment variables (see {@code application.yml}); missing/blank values fail Spring context
 * startup fast, mirroring {@link RrnHashConfig} / {@link InternalAiServiceConfig}. Credentials are
 * never logged (no {@code toString()} override that would print {@code accessKey}/{@code secretKey}).
 */
@Validated
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    @NotBlank(message = "minio.endpoint must not be blank — set MINIO_ENDPOINT")
    private String endpoint;

    @NotBlank(message = "minio.access-key must not be blank — set MINIO_ACCESS_KEY")
    private String accessKey;

    @NotBlank(message = "minio.secret-key must not be blank — set MINIO_SECRET_KEY")
    private String secretKey;

    @NotBlank(message = "minio.bucket must not be blank — set MINIO_BUCKET")
    private String bucket;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }
}
