package com.ai_kids_care.v1.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * D-STORE MinIO client bean. Bounded connect/write/read timeouts (hard constraint: object-storage
 * IO must never block a caller indefinitely) — evidence files are small/occasional (design.md), so
 * a 10s read budget is generous without risking an unbounded hang on a wedged MinIO endpoint.
 */
@Configuration
public class MinioClientConfig {

    private static final long CONNECT_TIMEOUT_MS = 5_000;
    private static final long WRITE_TIMEOUT_MS = 5_000;
    private static final long READ_TIMEOUT_MS = 10_000;

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        MinioClient client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
        client.setTimeout(CONNECT_TIMEOUT_MS, WRITE_TIMEOUT_MS, READ_TIMEOUT_MS);
        return client;
    }
}
