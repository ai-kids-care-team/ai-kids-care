package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.NotificationRuleCreateDTO;
import com.ai_kids_care.v1.dto.NotificationRuleUpdateDTO;
import com.ai_kids_care.v1.vo.NotificationRuleVO;
import com.ai_kids_care.v1.service.NotificationRuleService;
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

@Tag(name="NotificationRule")
@RestController
@RequestMapping("/api/v1/notification_rules")
@RequiredArgsConstructor
public class NotificationRuleController {

    private final NotificationRuleService service;

    @GetMapping
    public ResponseEntity<Page<NotificationRuleVO>> listNotificationRule(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listNotificationRules(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationRuleVO> getNotificationRule(@PathVariable Long id) {
        return ResponseEntity.ok(service.getNotificationRule(id));
    }

    @PostMapping
    public ResponseEntity<NotificationRuleVO> createNotificationRule(@RequestBody @Valid NotificationRuleCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createNotificationRule(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotificationRuleVO> updateNotificationRule(
            @PathVariable Long id,
            @RequestBody @Valid NotificationRuleUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateNotificationRule(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotificationRule(@PathVariable Long id) {
        service.deleteNotificationRule(id);
        return ResponseEntity.noContent().build();
    }
}