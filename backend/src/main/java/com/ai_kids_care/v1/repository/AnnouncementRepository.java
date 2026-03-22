package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Announcement;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
}