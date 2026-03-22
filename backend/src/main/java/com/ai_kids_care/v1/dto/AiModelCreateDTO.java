package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.AiModel}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiModelCreateDTO implements Serializable {
    private Long modelId;
    private String name;
    private String version;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}