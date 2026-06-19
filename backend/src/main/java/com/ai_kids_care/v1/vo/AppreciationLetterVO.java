package com.ai_kids_care.v1.vo;

import java.time.OffsetDateTime;

/**
 * SPEC-0003：感谢信公开响应 VO。
 *
 * 移除原始内部 ID（{@code kindergartenId}、{@code senderUserId}、{@code targetId}）和 {@code status}：
 * - sender 以解析后的显示名（{@code senderName}）替代原始 {@code senderUserId}（OQ-1）。
 * - target 以解析后的显示名（{@code targetName}）替代原始 {@code targetId}（OQ-1）。
 * - {@code kindergartenId} 对调用者隐含（OQ-1）。
 * - {@code status} 不在公开响应中回显（OQ-3）。
 */
public record AppreciationLetterVO(
        Long letterId,
        String senderName,
        String targetType,
        String targetName,
        String title,
        String content,
        Boolean isPublic,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
