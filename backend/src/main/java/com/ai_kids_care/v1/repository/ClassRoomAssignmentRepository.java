package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.ClassRoomAssignment;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface ClassRoomAssignmentRepository extends JpaRepository<ClassRoomAssignment, Long> {

    /** Active class ids covering a room at a given instant (start_at <= at < end_at, status ACTIVE). */
    @Query("SELECT DISTINCT cra.classes.id FROM ClassRoomAssignment cra "
            + "WHERE cra.rooms.id = :roomId AND cra.rooms.kindergarten.id = :kindergartenId "
            + "AND cra.status = :active "
            + "AND cra.startAt <= :at AND (cra.endAt IS NULL OR cra.endAt > :at)")
    List<Long> findActiveClassIds(@Param("roomId") Long roomId,
                                  @Param("kindergartenId") Long kindergartenId,
                                  @Param("active") StatusEnum active,
                                  @Param("at") OffsetDateTime at);
}
