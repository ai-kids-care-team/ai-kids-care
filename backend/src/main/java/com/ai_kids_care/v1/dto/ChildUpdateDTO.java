package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.Child}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChildUpdateDTO implements Serializable {
    private Long childId;
    private Long kindergartenId;
    private String name;
    private String childNo;
    private String rrnEncrypted;
    private String rrnFirst6;
    private LocalDate birthDate;
    private String gender;
    private String address;
    private LocalDate enrollDate;
    private LocalDate leaveDate;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}