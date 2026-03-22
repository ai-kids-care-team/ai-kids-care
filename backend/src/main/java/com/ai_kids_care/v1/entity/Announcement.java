package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "announcements")
public class Announcement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Size(max = 200)
    @NotNull
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @NotNull
    @Column(name = "body", nullable = false, length = Integer.MAX_VALUE)
    private String body;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned;

    @Column(name = "pinned_until")
    private OffsetDateTime pinnedUntil;

    @ColumnDefault("'ACTIVE'")
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;


}