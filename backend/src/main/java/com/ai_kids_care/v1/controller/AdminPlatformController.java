package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.service.PlatformAdminApprovalService;
import com.ai_kids_care.v1.vo.PendingRegistrationVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SPEC-0002 Slice B: 平台级管理员审批 / 拒绝 / 停用端点。
 *
 * 路径自然落入 SecurityConfig 默认 authenticated()，不在公开白名单。
 * 细粒度授权由 @PreAuthorize 粗粒度门 + PlatformPolicy（事务内）联合完成。
 * 不改动 SuperadminController / UserController 等（Phase 1A 已关闭其 CRUD/敏感字段）。
 */
@Tag(name = "Admin - Platform")
@RestController
@RequestMapping("/api/v1/admin/platform")
@RequiredArgsConstructor
public class AdminPlatformController {

    private final PlatformAdminApprovalService approvalService;

    /**
     * GET /api/v1/admin/platform/superadmin-registrations?status=PENDING
     * 列出 PENDING SUPERADMIN 申请（最小字段：userId、申请角色、提交时间；无 S1）。
     */
    @GetMapping("/superadmin-registrations")
    public ResponseEntity<List<PendingRegistrationVO>> listSuperadminRegistrations(
            @RequestParam(defaultValue = "PENDING") String status
    ) {
        // 当前 Slice B 只支持 status=PENDING；其他值直接按空列表返回（不报错）
        if (!"PENDING".equalsIgnoreCase(status)) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(approvalService.listPendingSuperadminRegistrations());
    }

    /**
     * POST /api/v1/admin/platform/superadmin-registrations/{userId}/approve
     * PENDING → ACTIVE（user + superadmin 档案 + PLATFORM role assignment）。
     * 返回 204 No Content。
     */
    @PostMapping("/superadmin-registrations/{userId}/approve")
    public ResponseEntity<Void> approveSuperadminRegistration(@PathVariable Long userId) {
        approvalService.approve(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/admin/platform/superadmin-registrations/{userId}/reject
     * PENDING → REJECTED。
     * 返回 204 No Content。
     */
    @PostMapping("/superadmin-registrations/{userId}/reject")
    public ResponseEntity<Void> rejectSuperadminRegistration(@PathVariable Long userId) {
        approvalService.reject(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/admin/platform/users/{userId}/disable
     * ACTIVE → DISABLED + 提交后吊销会话。
     * 返回 204 No Content。
     */
    @PostMapping("/users/{userId}/disable")
    public ResponseEntity<Void> disablePlatformUser(@PathVariable Long userId) {
        approvalService.disable(userId);
        return ResponseEntity.noContent().build();
    }
}
