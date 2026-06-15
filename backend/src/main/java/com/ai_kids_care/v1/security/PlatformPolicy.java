package com.ai_kids_care.v1.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * SPEC-0002 Slice B: 平台级细粒度资源关系判断（ADR-0019 §2 / §7）。
 *
 * 本 policy 在 @Transactional service 方法内调用，不独立发起事务。
 * 只做断言（assert*）——违反时直接抛 403，调用方不再重复判断。
 *
 * 粗粒度门（PLATFORM_IT_ADMIN + PLATFORM scope）已由 @PreAuthorize 完成；
 * 本类附加：
 *   禁自审：context.userId() != targetUserId
 *
 * 平台审批无 level 要求、无 tenant 绑定（SUPERADMIN 的 scope_id 为 NULL），
 * 故不需要 requireEligibleLevel / requireSameTenant 等园级方法。
 * tenant-aware 隔离由 PlatformAdminApprovalService 内的 scopeId IS NULL 条件更新实现。
 */
@Component
public class PlatformPolicy {

    /**
     * 断言调用者不是目标用户（禁自审）。
     * 违反时抛 403。
     */
    public void requireNotSelf(EffectiveAuthorizationContext context, Long targetUserId) {
        if (context.userId().equals(targetUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Cannot approve or modify your own registration");
        }
    }
}
