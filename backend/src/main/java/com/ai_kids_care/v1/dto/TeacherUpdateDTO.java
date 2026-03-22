package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.Teacher}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeacherUpdateDTO implements Serializable {
    private Long teacherId;
    private Long kindergartenId;
    private Long userId;
    private String staffNo;
    private String name;
    private String gender;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String rrnEncrypted;
    private String rrnFirst6;
    private String level;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}