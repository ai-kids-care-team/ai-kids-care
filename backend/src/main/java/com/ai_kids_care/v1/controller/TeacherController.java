package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.TeacherCreateDTO;
import com.ai_kids_care.v1.dto.TeacherUpdateDTO;
import com.ai_kids_care.v1.vo.TeacherVO;
import com.ai_kids_care.v1.service.TeacherService;
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

@Tag(name="Teacher")
@RestController
@RequestMapping("/api/v1/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService service;

    //url: api/v1/teachers/keyword=?Jerry&page=1&size=20?sort=created_at
    @GetMapping
    public ResponseEntity<Page<TeacherVO>> listTeacher(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listTeachers(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeacherVO> getTeacher(@PathVariable Long id) {
        return ResponseEntity.ok(service.getTeacher(id));
    }
    @Transactional(readOnly = true)
    @GetMapping("/by-user/{userId}")
    public ResponseEntity<TeacherVO> getTeacherNameByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(service.getTeacherNameByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<TeacherVO> createTeacher(@RequestBody TeacherCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTeacher(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeacherVO> updateTeacher(
            @PathVariable Long id,
            @RequestBody TeacherUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateTeacher(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeacher(@PathVariable Long id) {
        service.deleteTeacher(id);
        return ResponseEntity.noContent().build();
    }
}