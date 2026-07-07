package com.ai_kids_care.v1.internal;

import com.ai_kids_care.v1.dto.internal.DetectionEventIngestRequest;
import com.ai_kids_care.v1.dto.internal.DetectionEventIngestResponse;
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
 * AI → backend detection-event ingest (on alarm_on). Internal: auth by ROLE_AI_SERVICE,
 * @Hidden from OpenAPI. Idempotent on (kindergarten_id, dedup_key); fires an async staff alert
 * for a fresh event.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/internal/detection-events")
@RequiredArgsConstructor
public class DetectionEventIngestController {

    private final DetectionIngestService ingestService;

    @PostMapping
    public ResponseEntity<DetectionEventIngestResponse> ingest(
            @Valid @RequestBody DetectionEventIngestRequest request) {
        return ResponseEntity.ok(ingestService.ingestEvent(request));
    }
}
