package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.EventStatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "event_reviews", indexes = {
        @Index(name = "idx_review_event_time", columnList = "kindergarten_id, event_id, created_at")
})
public class EventReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private DetectionEvent detectionEvents;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "from_status", columnDefinition = "event_status_enum not null")
    private EventStatusEnum fromStatus;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "result_status", columnDefinition = "event_status_enum not null")
    private EventStatusEnum resultStatus;

    @Column(name = "comment", length = Integer.MAX_VALUE)
    private String comment;

    @NotNull
    @Column(name = "created_at")
    private OffsetDateTime createdAt;


}