package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.Superadmin}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SuperadminUpdateDTO implements Serializable {
    private Long superadminId;
    private Long userId;
    private String name;
    private String department;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}