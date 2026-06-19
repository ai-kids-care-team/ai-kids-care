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
 *
 * {@code editable} 为服务端派生的归属信号（= 当前调用者是否为本信作者）：FE 据此决定是否
 * 显示编辑/删除入口，而无需在客户端比对 senderUserId（符合 SPEC-0001：UI 不从客户端数据推断身份）。
 */
public record AppreciationLetterVO(
        Long letterId,
        String senderName,
        String targetType,
        String targetName,
        String title,
        String content,
        Boolean isPublic,
        Boolean editable,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
