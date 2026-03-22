package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.Kindergarten}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KindergartenUpdateDTO implements Serializable {
    private Long kindergartenId;
    private String name;
    private String address;
    private String regionCode;
    private String code;
    private String businessRegistrationNo;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}