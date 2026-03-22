package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.GuardianCreateDTO;
import com.ai_kids_care.v1.dto.GuardianUpdateDTO;
import com.ai_kids_care.v1.vo.GuardianVO;
import com.ai_kids_care.v1.service.GuardianService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name="Guardian")
@RestController
@RequestMapping("/api/v1/guardians")
@RequiredArgsConstructor
public class GuardianController {

    private final GuardianService service;

    @GetMapping
    public ResponseEntity<Page<GuardianVO>> listGuardian(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listGuardians(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GuardianVO> getGuardian(@PathVariable Long id) {
        return ResponseEntity.ok(service.getGuardian(id));
    }

    @PostMapping
    public ResponseEntity<GuardianVO> createGuardian(@RequestBody GuardianCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createGuardian(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GuardianVO> updateGuardian(
            @PathVariable Long id,
            @RequestBody GuardianUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateGuardian(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGuardian(@PathVariable Long id) {
        service.deleteGuardian(id);
        return ResponseEntity.noContent().build();
    }
}