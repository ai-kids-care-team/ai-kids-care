package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.UserKindergartenMembership;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserKindergartenMembershipRepository extends JpaRepository<UserKindergartenMembership, Long> {
}