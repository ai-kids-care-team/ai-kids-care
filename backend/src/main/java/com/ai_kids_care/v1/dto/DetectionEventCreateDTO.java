package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.DetectionEvent}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DetectionEventCreateDTO implements Serializable {
    private Long eventId;
    private Long kindergartenId;
    private Long cameraId;
    private Long roomId;
    private Long sessionId;
    private String eventType;
    private Integer severity;
    private Double confidence;
    private OffsetDateTime detectedAt;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}