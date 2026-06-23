package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.ChildGuardianRelationship;
import com.ai_kids_care.v1.entity.ChildGuardianRelationshipId;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChildGuardianRelationshipRepository extends JpaRepository<ChildGuardianRelationship, ChildGuardianRelationshipId> {
    Optional<ChildGuardianRelationship> findById_ChildIdAndId_GuardianId(Long childId, Long guardianId);

    /** Distinct guardian user_ids with an active relationship to any of the given children on a date. */
    @Query("SELECT DISTINCT cgr.guardians.user.id FROM ChildGuardianRelationship cgr "
            + "WHERE cgr.children.id IN :childIds AND cgr.kindergarten.id = :kindergartenId "
            + "AND cgr.guardians.status = :active "
            + "AND (cgr.endDate IS NULL OR cgr.endDate >= :date)")
    List<Long> findActiveGuardianUserIds(@Param("childIds") Collection<Long> childIds,
                                         @Param("kindergartenId") Long kindergartenId,
                                         @Param("active") StatusEnum active,
                                         @Param("date") LocalDate date);
}
