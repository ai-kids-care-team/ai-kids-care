package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.service.ChildrenService;
import com.ai_kids_care.v1.vo.GuardianChildVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SPEC-0001 §3 / §349：Guardian 关系-scoped 儿童读取。
 *
 * 仅发布 GET，且仅 GUARDIAN 角色可用（@PreAuthorize 在 service 层），返回最小 {@link GuardianChildVO}。
 * 细粒度「仅有 ACTIVE 关系的儿童」由 ChildRepository 的 SQL 强制；无关系 → 隐藏 404 + 审计。
 * 通用 create/update/delete 与完整 ChildVO 仍关闭（Phase 1A），不在此发布。
 */
@Tag(name = "Children")
@RestController
@RequestMapping("/api/v1/children")
@RequiredArgsConstructor
public class ChildrenController {

    private final ChildrenService childrenService;

    @GetMapping
    public ResponseEntity<List<GuardianChildVO>> listChildren() {
        return ResponseEntity.ok(childrenService.listRelatedChildren());
    }

    @GetMapping("/{id}")
    public ResponseEntity<GuardianChildVO> getChild(@PathVariable Long id) {
        return ResponseEntity.ok(childrenService.getRelatedChild(id));
    }
}
