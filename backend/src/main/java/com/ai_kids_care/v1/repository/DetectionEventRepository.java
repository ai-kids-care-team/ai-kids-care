package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.DetectionEvent;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DetectionEventRepository extends JpaRepository<DetectionEvent, Long> {

    /**
     * Dashboard list read. {@link com.ai_kids_care.v1.mapper.DetectionEventMapper#toVO} dereferences
     * {@code kindergarten.name}, {@code cctvCameras.cameraName} and {@code rooms.name} (all LAZY
     * {@code @ManyToOne}); without eager fetch this is an N+1 (one base SELECT + up to 3 per row). The
     * {@code @EntityGraph} fetch-joins those three to-ones so the page loads in a single SQL. The
     * fourth to-one {@code detectionSessions} is intentionally NOT fetched — the mapper only reads its
     * FK id, which the lazy proxy already holds without a query. Fetch-joining {@code @ManyToOne}
     * (to-one) with {@link Pageable} is safe: the Hibernate in-memory pagination warning only fires
     * for to-many fetch joins.
     */
    @EntityGraph(attributePaths = {"kindergarten", "cctvCameras", "rooms"})
    Page<DetectionEvent> findByKindergarten_Id(Long kindergartenId, Pageable pageable);

    /**
     * Single-event read shared by the dashboard detail endpoint, the SSE push lookup
     * ({@code DetectionEventService.getForPush}) and the review write path. Same {@code toVO} to-one
     * dereferences as the list query, so the {@code @EntityGraph} collapses the 1+3 lazy loads into a
     * single SELECT.
     */
    @EntityGraph(attributePaths = {"kindergarten", "cctvCameras", "rooms"})
    Optional<DetectionEvent> findByIdAndKindergarten_Id(Long id, Long kindergartenId);

    /**
     * SSE reconnect replay cursor query: detection events of one kindergarten whose {@code event_id}
     * is strictly greater than {@code lastEventId}, ordered <strong>descending</strong> and capped by
     * {@code limit}. Descending + limit so that when the missed window exceeds the bound we keep the
     * <em>most-recent</em> {@code limit} events (the service reverses them back to ascending for the
     * client); older history is covered by the read-API load. Tenant scope is enforced by the
     * {@code kindergarten_id} predicate — the numeric {@code lastEventId} is only a lower bound and
     * cannot widen scope. Backed by the unique {@code (kindergarten_id, event_id)} index.
     *
     * <p>The {@code @EntityGraph} fetch-joins the three to-ones dereferenced by
     * {@link com.ai_kids_care.v1.mapper.DetectionEventMapper#toVO} ({@code kindergarten},
     * {@code cctvCameras}, {@code rooms}) so the bounded replay set loads in a single SELECT instead of
     * 1+3·N. {@code detectionSessions} is left lazy (only its FK id is read).
     */
    @EntityGraph(attributePaths = {"kindergarten", "cctvCameras", "rooms"})
    List<DetectionEvent> findByKindergarten_IdAndIdGreaterThanOrderByIdDesc(
            Long kindergartenId, Long lastEventId, Limit limit);
}
