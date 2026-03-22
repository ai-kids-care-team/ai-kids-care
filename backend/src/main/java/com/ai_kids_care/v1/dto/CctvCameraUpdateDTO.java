package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.CctvCamera}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CctvCameraUpdateDTO implements Serializable {
    private Long cameraId;
    private Long kindergartenId;
    private String serialNo;
    private String cameraName;
    private String model;
    private Long createdByUserId;
    private String status;
    private OffsetDateTime lastSeenAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}