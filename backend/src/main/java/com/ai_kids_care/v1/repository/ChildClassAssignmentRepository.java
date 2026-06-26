package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.ChildClassAssignment;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ChildClassAssignmentRepository extends JpaRepository<ChildClassAssignment, Long> {

    /** Active child ids in the given classes on a given date (start_date <= date <= end_date, ACTIVE). */
    @Query("SELECT DISTINCT cca.children.id FROM ChildClassAssignment cca "
            + "WHERE cca.classes.id IN :classIds AND cca.children.kindergarten.id = :kindergartenId "
            + "AND cca.status = :active "
            + "AND cca.startDate <= :date AND (cca.endDate IS NULL OR cca.endDate >= :date)")
    List<Long> findActiveChildIds(@Param("classIds") Collection<Long> classIds,
                                  @Param("kindergartenId") Long kindergartenId,
                                  @Param("active") StatusEnum active,
                                  @Param("date") LocalDate date);
}
