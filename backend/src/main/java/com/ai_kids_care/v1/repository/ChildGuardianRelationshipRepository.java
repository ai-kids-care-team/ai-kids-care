package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.ChildGuardianRelationship;
import com.ai_kids_care.v1.entity.ChildGuardianRelationshipId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildGuardianRelationshipRepository extends JpaRepository<ChildGuardianRelationship, ChildGuardianRelationshipId> {
}