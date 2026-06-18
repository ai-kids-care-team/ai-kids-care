package com.ai_kids_care.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotBlank
    private String name;
    @NotBlank
    private String version;
    @NotNull
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
