package com.ai_kids_care.v1.internal;

import com.ai_kids_care.v1.dto.internal.DetectionSessionIngestRequest;
import com.ai_kids_care.v1.dto.internal.DetectionSessionIngestResponse;
import com.ai_kids_care.v1.service.DetectionIngestService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI → backend detection-session ingest (stream start). Internal: auth by ROLE_AI_SERVICE
 * (AiServiceTokenAuthenticationFilter + SecurityConfig hasRole), @Hidden from OpenAPI.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/internal/detection-sessions")
@RequiredArgsConstructor
public class DetectionSessionIngestController {

    private final DetectionIngestService ingestService;

    @PostMapping
    public ResponseEntity<DetectionSessionIngestResponse> create(
            @Valid @RequestBody DetectionSessionIngestRequest request) {
        return ResponseEntity.ok(ingestService.createSession(request));
    }
}
