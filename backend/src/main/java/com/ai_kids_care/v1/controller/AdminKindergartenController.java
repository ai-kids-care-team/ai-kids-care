package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.service.KindergartenAdminApprovalService;
import com.ai_kids_care.v1.vo.PendingRegistrationVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SPEC-0002 Slice A: 园级管理员审批 / 拒绝 / 停用端点。
 *
 * 路径自然落入 SecurityConfig 默认 authenticated()，不在公开白名单。
 * 细粒度授权由 @PreAuthorize 粗粒度门 + KindergartenAdminPolicy（事务内）联合完成。
 * 不改动 UserController / TeacherController / GuardianController（Phase 1A 已关闭其 CRUD/敏感字段）。
 */
@Tag(name = "Admin - Kindergarten")
@RestController
@RequestMapping("/api/v1/admin/kindergarten")
@RequiredArgsConstructor
public class AdminKindergartenController {

    private final KindergartenAdminApprovalService approvalService;

    /**
     * GET /api/v1/admin/kindergarten/registrations?status=PENDING
     * 列出本园 PENDING 申请（最小字段：userId、申请角色、level、提交时间；不含 S1）。
     */
    @GetMapping("/registrations")
    public ResponseEntity<List<PendingRegistrationVO>> listRegistrations(
            @RequestParam(defaultValue = "PENDING") String status
    ) {
        // 当前 Slice A 只支持 status=PENDING；其他值直接按空列表返回（不报错）
        if (!"PENDING".equalsIgnoreCase(status)) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(approvalService.listPendingRegistrations());
    }

    /**
     * POST /api/v1/admin/kindergarten/registrations/{userId}/approve
     * PENDING → ACTIVE（user + 档案 + membership + role assignment）。
     * 返回 204 No Content。
     */
    @PostMapping("/registrations/{userId}/approve")
    public ResponseEntity<Void> approveRegistration(@PathVariable Long userId) {
        approvalService.approve(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/admin/kindergarten/registrations/{userId}/reject
     * PENDING → REJECTED。
     * 返回 204 No Content。
     */
    @PostMapping("/registrations/{userId}/reject")
    public ResponseEntity<Void> rejectRegistration(@PathVariable Long userId) {
        approvalService.reject(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/admin/kindergarten/members/{userId}/disable
     * ACTIVE → DISABLED + 提交后吊销会话。
     * 返回 204 No Content。
     */
    @PostMapping("/members/{userId}/disable")
    public ResponseEntity<Void> disableMember(@PathVariable Long userId) {
        approvalService.disable(userId);
        return ResponseEntity.noContent().build();
    }
}
