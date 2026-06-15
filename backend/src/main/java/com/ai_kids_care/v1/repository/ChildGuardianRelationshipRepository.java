package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.ChildGuardianRelationship;
import com.ai_kids_care.v1.entity.ChildGuardianRelationshipId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChildGuardianRelationshipRepository extends JpaRepository<ChildGuardianRelationship, ChildGuardianRelationshipId> {
    Optional<ChildGuardianRelationship> findById_ChildIdAndId_GuardianId(Long childId, Long guardianId);
}