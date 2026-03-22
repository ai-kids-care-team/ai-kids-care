package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.EventEvidenceFileCreateDTO;
import com.ai_kids_care.v1.dto.EventEvidenceFileUpdateDTO;
import com.ai_kids_care.v1.vo.EventEvidenceFileVO;
import com.ai_kids_care.v1.service.EventEvidenceFileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name="EventEvidenceFile")
@RestController
@RequestMapping("/api/v1/event_evidence_files")
@RequiredArgsConstructor
public class EventEvidenceFileController {

    private final EventEvidenceFileService service;

    @GetMapping
    public ResponseEntity<Page<EventEvidenceFileVO>> listEventEvidenceFile(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listEventEvidenceFiles(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventEvidenceFileVO> getEventEvidenceFile(@PathVariable Long id) {
        return ResponseEntity.ok(service.getEventEvidenceFile(id));
    }

    @PostMapping
    public ResponseEntity<EventEvidenceFileVO> createEventEvidenceFile(@RequestBody EventEvidenceFileCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createEventEvidenceFile(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventEvidenceFileVO> updateEventEvidenceFile(
            @PathVariable Long id,
            @RequestBody EventEvidenceFileUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateEventEvidenceFile(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEventEvidenceFile(@PathVariable Long id) {
        service.deleteEventEvidenceFile(id);
        return ResponseEntity.noContent().build();
    }
}