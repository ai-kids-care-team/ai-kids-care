package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.NotificationCreateDTO;
import com.ai_kids_care.v1.dto.NotificationUpdateDTO;
import com.ai_kids_care.v1.vo.NotificationVO;
import com.ai_kids_care.v1.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name="Notification")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public ResponseEntity<Page<NotificationVO>> listNotification(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listNotifications(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationVO> getNotification(@PathVariable Long id) {
        return ResponseEntity.ok(service.getNotification(id));
    }

    @PostMapping
    public ResponseEntity<NotificationVO> createNotification(@RequestBody @Valid NotificationCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createNotification(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotificationVO> updateNotification(
            @PathVariable Long id,
            @RequestBody @Valid NotificationUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateNotification(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        service.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }
}