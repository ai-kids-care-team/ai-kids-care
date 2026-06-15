package com.ai_kids_care.v1.security;

import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import com.ai_kids_care.v1.vo.AuthSessionVO;

public record EffectiveAuthorizationContext(
        Long userId,
        String loginId,
        Long roleAssignmentId,
        UserRoleEnum role,
        UserRoleAssignmentScopeType scopeType,
        Long activeKindergartenId,
        Long selectedKindergartenId,
        Long membershipId
) {
    public Long effectiveKindergartenId() {
        return scopeType == UserRoleAssignmentScopeType.KINDERGARTEN
                ? activeKindergartenId
                : selectedKindergartenId;
    }

    public AuthSessionVO toSessionProfile() {
        return new AuthSessionVO(
                userId,
                loginId,
                role.name(),
                scopeType.name(),
                activeKindergartenId
        );
    }
}
