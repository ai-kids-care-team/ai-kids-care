package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.AppreciationLetterCreateDTO;
import com.ai_kids_care.v1.dto.AppreciationLetterUpdateDTO;
import com.ai_kids_care.v1.service.AppreciationLetterService;
import com.ai_kids_care.v1.vo.AppreciationLetterVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SPEC-0003：感谢信读写端点（极薄 Controller；授权在 Service 层）。
 *
 * 发布于 {@code /api/v1/appreciation_letters}：
 * - GET（列表，分页 + keyword）
 * - GET /{id}（详情）
 * - POST（GUARDIAN 创建）
 * - PUT /{id}（GUARDIAN 更新本人信）
 * - DELETE /{id}（GUARDIAN 软删本人信）
 */
@Tag(name = "AppreciationLetters")
@RestController
@RequestMapping("/api/v1/appreciation_letters")
@RequiredArgsConstructor
public class AppreciationLetterController {

    private final AppreciationLetterService service;

    @GetMapping
    public ResponseEntity<Page<AppreciationLetterVO>> list(
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        return ResponseEntity.ok(service.listAppreciationLetters(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppreciationLetterVO> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getAppreciationLetter(id));
    }

    @PostMapping
    public ResponseEntity<AppreciationLetterVO> create(
            @Valid @RequestBody AppreciationLetterCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAppreciationLetter(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppreciationLetterVO> update(
            @PathVariable Long id,
            @Valid @RequestBody AppreciationLetterUpdateDTO dto) {
        return ResponseEntity.ok(service.updateAppreciationLetter(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteAppreciationLetter(id);
        return ResponseEntity.noContent().build();
    }
}
