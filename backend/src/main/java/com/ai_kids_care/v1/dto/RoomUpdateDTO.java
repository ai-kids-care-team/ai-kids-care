package com.ai_kids_care.v1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.Room}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomUpdateDTO implements Serializable {
    private Long roomId;
    private Long kindergartenId;
    @NotBlank
    private String name;
    private String roomCode;
    private String locationNote;
    @NotBlank
    private String roomType;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
