package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.EventReviewCreateDTO;
import com.ai_kids_care.v1.dto.EventReviewUpdateDTO;
import com.ai_kids_care.v1.vo.EventReviewVO;
import com.ai_kids_care.v1.service.EventReviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name="EventReview")
@RestController
@RequestMapping("/api/v1/event_reviews")
@RequiredArgsConstructor
public class EventReviewController {

    private final EventReviewService service;

    @GetMapping
    public ResponseEntity<Page<EventReviewVO>> listEventReview(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listEventReviews(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventReviewVO> getEventReview(@PathVariable Long id) {
        return ResponseEntity.ok(service.getEventReview(id));
    }

    @Transactional(readOnly = true)
    @GetMapping("/{eventId}/reviews/latest")
    public ResponseEntity<EventReviewVO> getLatestReview( @PathVariable Long eventId) {
        return ResponseEntity.ok(service.getLatestReview(eventId));
    }

    @Transactional(readOnly = true)
    @GetMapping("{eventId}/reviews")
    public ResponseEntity<List<EventReviewVO>> listReviewsByEventId(@PathVariable Long eventId) {
        return ResponseEntity.ok(service.listReviewsByEventId(eventId));
    }

    @Transactional
    @PostMapping
    public ResponseEntity<EventReviewVO> createEventReview(@RequestBody EventReviewCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createEventReview(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventReviewVO> updateEventReview(
            @PathVariable Long id,
            @RequestBody EventReviewUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateEventReview(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEventReview(@PathVariable Long id) {
        service.deleteEventReview(id);
        return ResponseEntity.noContent().build();
    }
}