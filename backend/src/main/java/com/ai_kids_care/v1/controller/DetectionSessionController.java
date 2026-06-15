package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.vo.DetectionSessionVO;
import com.ai_kids_care.v1.service.DetectionSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name="DetectionSession")
@RestController
@RequestMapping("/api/v1/detection_sessions")
@RequiredArgsConstructor
public class DetectionSessionController {

    private final DetectionSessionService service;

    @GetMapping
    public ResponseEntity<Page<DetectionSessionVO>> listDetectionSession(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listDetectionSessions(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DetectionSessionVO> getDetectionSession(@PathVariable Long id) {
        return ResponseEntity.ok(service.getDetectionSession(id));
    }
}
