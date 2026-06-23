package com.ai_kids_care.v1.internal;

import jakarta.validation.constraints.NotNull;

/**
 * AI → backend detection-session ingest (stream start). The backend derives kindergarten_id and
 * camera_id from the stream; the AI only knows the stream + its own model.
 */
public record DetectionSessionIngestRequest(
        @NotNull Long streamId,
        @NotNull Long modelId
) {
}
