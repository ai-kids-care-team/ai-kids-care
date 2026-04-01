package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.DetectionEventCreateDTO;
import com.ai_kids_care.v1.dto.DetectionEventUpdateDTO;
import com.ai_kids_care.v1.vo.DetectionEventVO;
import com.ai_kids_care.v1.service.DetectionEventService;
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

@Tag(name="DetectionEvent")
@RestController
@RequestMapping("/api/v1/detection_events")
@RequiredArgsConstructor
public class DetectionEventController {

    private final DetectionEventService service;

    @Transactional(readOnly = true)
    @GetMapping
    public ResponseEntity<Page<DetectionEventVO>> listDetectionEvent(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listDetectionEvents(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DetectionEventVO> getDetectionEvent(@PathVariable Long id) {
        return ResponseEntity.ok(service.getDetectionEvent(id));
    }

    @PostMapping
    public ResponseEntity<DetectionEventVO> createDetectionEvent(@RequestBody DetectionEventCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDetectionEvent(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DetectionEventVO> updateDetectionEvent(
            @PathVariable Long id,
            @RequestBody DetectionEventUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateDetectionEvent(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDetectionEvent(@PathVariable Long id) {
        service.deleteDetectionEvent(id);
        return ResponseEntity.noContent().build();
    }
}