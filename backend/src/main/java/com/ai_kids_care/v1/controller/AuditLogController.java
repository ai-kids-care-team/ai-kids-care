package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.AuditLogCreateDTO;
import com.ai_kids_care.v1.dto.AuditLogUpdateDTO;
import com.ai_kids_care.v1.vo.AuditLogVO;
import com.ai_kids_care.v1.service.AuditLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name="AuditLog")
@RestController
@RequestMapping("/api/v1/audit_logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService service;

    @GetMapping
    public ResponseEntity<Page<AuditLogVO>> listAuditLog(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listAuditLogs(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogVO> getAuditLog(@PathVariable Long id) {
        return ResponseEntity.ok(service.getAuditLog(id));
    }

    @PostMapping
    public ResponseEntity<AuditLogVO> createAuditLog(@RequestBody AuditLogCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAuditLog(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AuditLogVO> updateAuditLog(
            @PathVariable Long id,
            @RequestBody AuditLogUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateAuditLog(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAuditLog(@PathVariable Long id) {
        service.deleteAuditLog(id);
        return ResponseEntity.noContent().build();
    }
}