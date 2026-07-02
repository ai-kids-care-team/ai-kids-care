package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.DetectionEvent;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /**
     * Dashboard list read with an optional {@code keyword} filter (detection-event-keyword-search).
     * The tenant predicate ({@code d.kindergarten.id = :kindergartenId}) is AND-ed <em>outside</em> the
     * fully-parenthesized keyword OR-group, so no keyword value can ever widen scope past the caller's
     * active kindergarten. Blank/absent {@code keyword} is a no-op (identical result set to
     * {@link #findByKindergarten_Id}), so the service can call this method unconditionally.
     *
     * <p>Matches only non-PII, human-meaningful fields: camera name, room name, and the event-type
     * <strong>enum name</strong> (e.g. {@code ASSAULT}) — not the Korean display label, which is
     * frontend i18n after ADR-0013 and unavailable server-side.
     *
     * <p>The event-type match is expressed as {@code d.eventType in :eventTypes} rather than an HQL
     * {@code cast(d.eventType as string)}: the column is a Postgres custom enum type
     * ({@code event_type_enum}, mapped via {@code @JdbcTypeCode(SqlTypes.NAMED_ENUM)}), and Hibernate's
     * generic string cast does not reliably target custom enum column types. The service resolves which
     * {@link com.ai_kids_care.v1.type.EventTypeEnum} constants' names contain the keyword in Java and
     * passes them as a bound {@code IN} list — still a single query, no row load. The caller always
     * passes a non-null (possibly empty) list; HQL {@code in :eventTypes} with an empty bound
     * collection is valid and simply matches no rows (unlike a literal empty {@code IN ()}, which is
     * invalid HQL/SQL syntax — the parameterized form does not hit that syntax restriction). Note: an
     * HQL {@code :param is empty} predicate is NOT usable here — Hibernate 6 requires the operand of
     * {@code is empty} to be a plural association path, not a bind parameter (verified via a real
     * Hibernate/Postgres test run against this schema, per design D1's stated risk).
     */
    @EntityGraph(attributePaths = {"kindergarten", "cctvCameras", "rooms"})
    @Query("""
            select d from DetectionEvent d
            where d.kindergarten.id = :kindergartenId
              and (:keyword is null or :keyword = ''
                   or lower(d.cctvCameras.cameraName) like lower(concat('%', :keyword, '%'))
                   or lower(d.rooms.name)            like lower(concat('%', :keyword, '%'))
                   or d.eventType in :eventTypes)
            """)
    Page<DetectionEvent> searchByKindergarten(
            @Param("kindergartenId") Long kindergartenId,
            @Param("keyword") String keyword,
            @Param("eventTypes") Collection<com.ai_kids_care.v1.type.EventTypeEnum> eventTypes,
            Pageable pageable);
}
