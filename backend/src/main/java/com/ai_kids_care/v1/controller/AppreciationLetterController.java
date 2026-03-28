package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.AppreciationLetterCreateDTO;
import com.ai_kids_care.v1.dto.AppreciationLetterUpdateDTO;
import com.ai_kids_care.v1.vo.AppreciationLetterVO;
import com.ai_kids_care.v1.service.AppreciationLetterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name="AppreciationLetter")
@RestController
@RequestMapping("/api/v1/appreciation_letters")
@RequiredArgsConstructor
public class AppreciationLetterController {

    private final AppreciationLetterService service;

    @GetMapping
    public ResponseEntity<Page<AppreciationLetterVO>> listAppreciationLetter(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listAppreciationLetters(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppreciationLetterVO> getAppreciationLetter(@PathVariable Long id) {
        return ResponseEntity.ok(service.getAppreciationLetter(id));
    }

    @PostMapping
    public ResponseEntity<AppreciationLetterVO> createAppreciationLetter(@RequestBody AppreciationLetterCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAppreciationLetter(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppreciationLetterVO> updateAppreciationLetter(
            @PathVariable Long id,
            @RequestBody AppreciationLetterUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateAppreciationLetter(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAppreciationLetter(@PathVariable Long id) {
        service.deleteAppreciationLetter(id);
        return ResponseEntity.noContent().build();
    }
}