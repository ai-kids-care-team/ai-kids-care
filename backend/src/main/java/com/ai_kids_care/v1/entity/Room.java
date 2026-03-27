package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "rooms", schema = "public", indexes = {
        @Index(name = "uq_room_kg_roomid", columnList = "kindergarten_id, room_id", unique = true),
        @Index(name = "uq_room_kg_roomcode", columnList = "kindergarten_id, room_code", unique = true)
})
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kindergarten_id", nullable = false)
    private Kindergarten kindergarten;

    @Column(name = "name", length = Integer.MAX_VALUE)
    private String name;

    @Column(name = "room_code", length = Integer.MAX_VALUE)
    private String roomCode;

    @Column(name = "location_note", length = Integer.MAX_VALUE)
    private String locationNote;

    @Column(name = "room_type", length = Integer.MAX_VALUE)
    private String roomType;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}