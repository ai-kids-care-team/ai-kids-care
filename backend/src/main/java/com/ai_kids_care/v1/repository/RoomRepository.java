package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
}