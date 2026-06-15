package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Page<Room> findAllByKindergarten_Id(Long kindergartenId, Pageable pageable);

    Optional<Room> findByIdAndKindergarten_Id(Long id, Long kindergartenId);

    /**
     * Rooms in the tenant that the given user reaches through the assignment chain
     * (ADR-0019 §7): an ACTIVE class_room_assignment (its window containing {@code nowTs})
     * links the room to a class the user covers through an ACTIVE class_teacher_assignment
     * (its window containing {@code today}), with both the teacher profile and the
     * assignments ACTIVE.
     */
    @Query("""
            select r from Room r
            where r.kindergarten.id = :kindergartenId
              and exists (
                  select cra.id from ClassRoomAssignment cra
                  where cra.rooms = r
                    and cra.status = 'ACTIVE'
                    and cra.startAt <= :nowTs
                    and (cra.endAt is null or cra.endAt >= :nowTs)
                    and exists (
                        select cta.id from ClassTeacherAssignment cta
                        where cta.classes = cra.classes
                          and cta.teachers.user.id = :userId
                          and cta.teachers.status = 'ACTIVE'
                          and cta.status = 'ACTIVE'
                          and cta.startDate <= :today
                          and (cta.endDate is null or cta.endDate >= :today)
                    )
              )
            """)
    Page<Room> findActivelyAssignedRoomsForTeacher(
            @Param("kindergartenId") Long kindergartenId,
            @Param("userId") Long userId,
            @Param("nowTs") OffsetDateTime nowTs,
            @Param("today") LocalDate today,
            Pageable pageable);

    @Query("""
            select r from Room r
            where r.id = :id
              and r.kindergarten.id = :kindergartenId
              and exists (
                  select cra.id from ClassRoomAssignment cra
                  where cra.rooms = r
                    and cra.status = 'ACTIVE'
                    and cra.startAt <= :nowTs
                    and (cra.endAt is null or cra.endAt >= :nowTs)
                    and exists (
                        select cta.id from ClassTeacherAssignment cta
                        where cta.classes = cra.classes
                          and cta.teachers.user.id = :userId
                          and cta.teachers.status = 'ACTIVE'
                          and cta.status = 'ACTIVE'
                          and cta.startDate <= :today
                          and (cta.endDate is null or cta.endDate >= :today)
                    )
              )
            """)
    Optional<Room> findActivelyAssignedRoomForTeacher(
            @Param("id") Long id,
            @Param("kindergartenId") Long kindergartenId,
            @Param("userId") Long userId,
            @Param("nowTs") OffsetDateTime nowTs,
            @Param("today") LocalDate today);
}
