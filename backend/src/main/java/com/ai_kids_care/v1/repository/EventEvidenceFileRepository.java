package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.EventEvidenceFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventEvidenceFileRepository extends JpaRepository<EventEvidenceFile, Long> {

    /**
     * D-STORE list-by-event read. {@link EventEvidenceFile} has no direct {@code kindergarten_id}
     * column of its own (only the {@code event_id} FK to {@link com.ai_kids_care.v1.entity.DetectionEvent}),
     * so the tenant predicate is expressed via the derived query's {@code DetectionEvents_Kindergarten_Id}
     * path — Spring Data compiles this to a real SQL join + WHERE predicate against
     * {@code detection_events.kindergarten_id}, never a load-then-filter.
     */
    List<EventEvidenceFile> findByDetectionEvents_IdAndDetectionEvents_Kindergarten_Id(
            Long eventId, Long kindergartenId);

    /**
     * D-STORE content-read lookup — same join-based tenant predicate as above, scoped to a single
     * evidence row by its own id. Cross-tenant / nonexistent evidenceId -> empty (caller 404s).
     */
    Optional<EventEvidenceFile> findByIdAndDetectionEvents_Kindergarten_Id(Long id, Long kindergartenId);
}