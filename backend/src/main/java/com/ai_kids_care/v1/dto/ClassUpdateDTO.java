package com.ai_kids_care.v1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.Class}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClassUpdateDTO implements Serializable {
    private Long classId;
    private Long kindergartenId;
    @NotBlank
    private String name;
    @NotBlank
    private String grade;
    private Long academicYear;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
