package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.service.NotificationService;
import com.ai_kids_care.v1.vo.NotificationReadVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * SPEC-0001 / ADR-0018 A3d：通知读取（tenant-scoped）+ wire-notification-read-state / D3：per-user
 * 已读状态（mark-read / unread-count）。
 *
 * GET 授权在 service 层（@PreAuthorize），返回最小 {@link NotificationReadVO}。
 * 受体（GUARDIAN/TEACHER）只能读取自己的通知；KINDERGARTEN_ADMIN 可读取所在园的全部通知。
 * 细粒度「受体仅自己 / Admin 仅本园」由 NotificationRepository SQL 强制；无权限 → 隐藏 404 + 审计。
 * {@code PATCH /{id}/read} 是本控制器唯一的写操作——受 CSRF 强制，幂等，越权/跨租户/不存在一律隐藏 404。
 * 其余写操作（POST/PUT/DELETE）未发布（Phase 1A），路径存在但无 handler → 405。
 */
@Tag(name = "Notification")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationReadVO>> listNotifications() {
        return ResponseEntity.ok(notificationService.listNotifications());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.unreadCount()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationReadVO> getNotification(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.getNotification(id));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationReadVO> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ResponseEntity.ok(notificationService.getNotification(id));
    }
}
