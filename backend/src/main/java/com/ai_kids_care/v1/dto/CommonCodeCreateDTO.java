package com.ai_kids_care.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.CommonCode}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommonCodeCreateDTO implements Serializable {
    private Long codeId;
    @Size(max = 50)
    private String parentCode;
    @NotBlank
    @Size(max = 50)
    private String codeGroup;
    @NotBlank
    @Size(max = 50)
    private String code;
    @NotBlank
    @Size(max = 100)
    private String codeName;
    @NotNull
    private Integer sortOrder;
    @NotNull
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
