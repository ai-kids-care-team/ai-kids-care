package com.ai_kids_care.v1.dto;

import com.fasterxml.jackson.databind.JsonNode;
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
public class CommonCodeUpdateDTO implements Serializable {
    private Long codeId;
    private String parentCode;
    private String codeGroup;
    private String code;
    private String codeName;
    private Integer sortOrder;
    private Boolean isActive;
    private JsonNode extraJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}