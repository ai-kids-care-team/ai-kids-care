package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.AiModelCreateDTO;
import com.ai_kids_care.v1.dto.AiModelUpdateDTO;
import com.ai_kids_care.v1.vo.AiModelVO;
import com.ai_kids_care.v1.service.AiModelService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name="AiModel")
@RestController
@RequestMapping("/api/v1/ai_models")
@RequiredArgsConstructor
public class AiModelController {

    private final AiModelService service;

    @GetMapping
    public ResponseEntity<Page<AiModelVO>> listAiModel(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listAiModels(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiModelVO> getAiModel(@PathVariable Long id) {
        return ResponseEntity.ok(service.getAiModel(id));
    }

    @PostMapping
    public ResponseEntity<AiModelVO> createAiModel(@RequestBody AiModelCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAiModel(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AiModelVO> updateAiModel(
            @PathVariable Long id,
            @RequestBody AiModelUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateAiModel(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAiModel(@PathVariable Long id) {
        service.deleteAiModel(id);
        return ResponseEntity.noContent().build();
    }
}