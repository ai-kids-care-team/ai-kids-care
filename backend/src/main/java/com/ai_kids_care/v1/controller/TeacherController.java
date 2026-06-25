package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.service.TeacherService;
import com.ai_kids_care.v1.vo.TeacherVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GT-1 / SEC-07: tenant-scoped teacher read endpoints. Every method delegates to
 * {@link TeacherService}, which enforces the coarse {@code TENANT_S2_READ} gate
 * (KINDERGARTEN_ADMIN + TEACHER) and a mandatory active-kindergarten filter, so the
 * caller only ever sees same-kindergarten teachers. Endpoints mirror the frontend
 * {@code teachers.api.ts} contract (searchTeachers / getTeacher / getTeacherByUserId).
 */
@Tag(name = "Teacher")
@RestController
@RequestMapping("/api/v1/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService service;

    @GetMapping
    public ResponseEntity<Page<TeacherVO>> listTeachers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listTeachers(keyword, userId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeacherVO> getTeacher(@PathVariable Long id) {
        return ResponseEntity.ok(service.getTeacher(id));
    }

    @GetMapping("/by-user/{userId}")
    public ResponseEntity<TeacherVO> getTeacherByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(service.getTeacherByUserId(userId));
    }
}
