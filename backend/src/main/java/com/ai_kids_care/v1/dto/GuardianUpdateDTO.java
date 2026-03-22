package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.Guardian}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GuardianUpdateDTO implements Serializable {
    private Long guardianId;
    private Long kindergartenId;
    private Long userId;
    private String name;
    private String rrnEncrypted;
    private String rrnFirst6;
    private String gender;
    private String address;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}