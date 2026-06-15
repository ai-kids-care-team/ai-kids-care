package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.UserKindergartenMembership;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserKindergartenMembershipRepository extends JpaRepository<UserKindergartenMembership, Long> {
    List<UserKindergartenMembership> findAllByUser_IdAndStatus(Long userId, StatusEnum status);
}
