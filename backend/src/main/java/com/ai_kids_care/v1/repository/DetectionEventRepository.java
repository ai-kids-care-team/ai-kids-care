package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.DetectionEvent;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DetectionEventRepository extends JpaRepository<DetectionEvent, Long> {

    Page<DetectionEvent> findByKindergarten_Id(Long kindergartenId, Pageable pageable);

    Optional<DetectionEvent> findByIdAndKindergarten_Id(Long id, Long kindergartenId);

    /**
     * SSE reconnect replay cursor query: detection events of one kindergarten whose {@code event_id}
     * is strictly greater than {@code lastEventId}, ordered <strong>descending</strong> and capped by
     * {@code limit}. Descending + limit so that when the missed window exceeds the bound we keep the
     * <em>most-recent</em> {@code limit} events (the service reverses them back to ascending for the
     * client); older history is covered by the read-API load. Tenant scope is enforced by the
     * {@code kindergarten_id} predicate — the numeric {@code lastEventId} is only a lower bound and
     * cannot widen scope. Backed by the unique {@code (kindergarten_id, event_id)} index.
     */
    List<DetectionEvent> findByKindergarten_IdAndIdGreaterThanOrderByIdDesc(
            Long kindergartenId, Long lastEventId, Limit limit);
}
