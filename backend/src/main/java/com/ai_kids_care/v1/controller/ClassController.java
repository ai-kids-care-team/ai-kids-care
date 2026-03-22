package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.ClassCreateDTO;
import com.ai_kids_care.v1.dto.ClassUpdateDTO;
import com.ai_kids_care.v1.vo.ClassVO;
import com.ai_kids_care.v1.service.ClassService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name="Class")
@RestController
@RequestMapping("/api/v1/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService service;

    @GetMapping
    public ResponseEntity<Page<ClassVO>> listClass(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listClasses(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClassVO> getClass(@PathVariable Long id) {
        return ResponseEntity.ok(service.getClass(id));
    }

    @PostMapping
    public ResponseEntity<ClassVO> createClass(@RequestBody ClassCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createClass(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClassVO> updateClass(
            @PathVariable Long id,
            @RequestBody ClassUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateClass(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClass(@PathVariable Long id) {
        service.deleteClass(id);
        return ResponseEntity.noContent().build();
    }
}