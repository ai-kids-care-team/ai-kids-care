package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.EventReview}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventReviewCreateDTO implements Serializable {
    private Long reviewId;
    private Long eventId;
    private Long kindergartenId;
    private Long userId;
    private String fromStatus;
    private String resultStatus;
    private String comment;
    private OffsetDateTime createdAt;
}