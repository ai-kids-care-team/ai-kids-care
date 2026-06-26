package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.EventReviewCreateDTO;
import com.ai_kids_care.v1.service.EventReviewService;
import com.ai_kids_care.v1.vo.EventReviewVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Event review workflow (closed-loop step 2). Staff (KINDERGARTEN_ADMIN/TEACHER) confirm a detection
 * event review and read review history. Authorization + tenant scoping enforced in the service
 * (@PreAuthorize EVENT_REVIEW_WRITE/READ + EffectiveAuthorizationContext). Confirm fires no
 * notification yet (guardian notify is step 3).
 */
@Tag(name = "EventReview")
@RestController
@RequestMapping("/api/v1/event_reviews")
@RequiredArgsConstructor
public class EventReviewController {

    private final EventReviewService service;

    @PostMapping
    public ResponseEntity<EventReviewVO> confirm(@Valid @RequestBody EventReviewCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.confirm(dto));
    }

    @GetMapping
    public ResponseEntity<Page<EventReviewVO>> list(
            @RequestParam(required = false) Long eventId, Pageable pageable) {
        return ResponseEntity.ok(service.listEventReviews(eventId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventReviewVO> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getEventReview(id));
    }
}
