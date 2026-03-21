package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.Room}
 */
public record RoomVO(
        Long roomId,
        Long kindergartenId,
        String name,
        String roomCode,
        String locationNote,
        String roomType,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}