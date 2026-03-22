package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.SuperadminCreateDTO;
import com.ai_kids_care.v1.dto.SuperadminUpdateDTO;
import com.ai_kids_care.v1.vo.SuperadminVO;
import com.ai_kids_care.v1.service.SuperadminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name="Superadmin")
@RestController
@RequestMapping("/api/v1/superadmins")
@RequiredArgsConstructor
public class SuperadminController {

    private final SuperadminService service;

    @GetMapping
    public ResponseEntity<Page<SuperadminVO>> listSuperadmin(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listSuperadmins(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuperadminVO> getSuperadmin(@PathVariable Long id) {
        return ResponseEntity.ok(service.getSuperadmin(id));
    }

    @PostMapping
    public ResponseEntity<SuperadminVO> createSuperadmin(@RequestBody SuperadminCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSuperadmin(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuperadminVO> updateSuperadmin(
            @PathVariable Long id,
            @RequestBody SuperadminUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateSuperadmin(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSuperadmin(@PathVariable Long id) {
        service.deleteSuperadmin(id);
        return ResponseEntity.noContent().build();
    }
}