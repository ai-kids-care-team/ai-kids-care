package com.ai_kids_care.v1.dto;

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
    private String name;
    private String roomCode;
    private String locationNote;
    private String roomType;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}