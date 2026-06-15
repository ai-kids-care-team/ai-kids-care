package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Page<Room> findAllByKindergarten_Id(Long kindergartenId, Pageable pageable);

    Optional<Room> findByIdAndKindergarten_Id(Long id, Long kindergartenId);
}
