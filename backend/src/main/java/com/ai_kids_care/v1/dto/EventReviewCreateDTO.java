package com.ai_kids_care.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("event_id")
    private Long eventId;

    private Long kindergartenId;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("from_status")
    private String fromStatus;

    @JsonProperty("result_status")
    private String resultStatus;

    private String comment;
    private OffsetDateTime createdAt;
}