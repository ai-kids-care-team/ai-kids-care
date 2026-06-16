package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * SPEC-0002 Slice A: 园级 PENDING 申请列表的最小 VO。
 *
 * 仅包含 userId（主键）、申请角色、申请者 level（Teacher/KindergartenAdmin 时有效）、
 * 提交时间。不回显 RRN、联系方式等 S1 字段（SPEC-0002 §契约）。
 */
public record PendingRegistrationVO(
        Long userId,
        String role,
        String level,
        OffsetDateTime submittedAt
) implements Serializable {
}
