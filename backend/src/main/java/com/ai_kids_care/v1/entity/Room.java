package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Getter
@Setter
@Accessors(chain = true)
@Entity
@Table(name = "rooms", schema = "public", indexes = {
        @Index(name = "uq_room_kg_roomid",
                columnList = "kindergarten_id, room_id",
                unique = true),
        @Index(name = "uq_room_kg_roomcode",
                columnList = "kindergarten_id, room_code",
                unique = true)})
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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @ColumnDefault("'2026-03-17 12:56:22.061799+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("'2026-03-17 12:56:22.061799+00'")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}