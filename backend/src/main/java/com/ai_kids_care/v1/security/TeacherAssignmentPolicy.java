package com.ai_kids_care.v1.security;

import com.ai_kids_care.v1.type.UserRoleEnum;
import org.springframework.stereotype.Component;

/**
 * Central decision for whether a principal's tenant resource access must be narrowed
 * to their effective assignments (ADR-0019 §7).
 *
 * A {@code TEACHER} is assignment-scoped: they may only reach classes (and, as the
 * relationship chain is migrated, the rooms/cameras/events derived from them) covered
 * by an active assignment. A {@code KINDERGARTEN_ADMIN} keeps kindergarten-wide access.
 * The definition of an "effective assignment" (ACTIVE status within its date window)
 * lives in the scoped repository queries, so the relationship condition is enforced in
 * SQL rather than by post-filtering loaded rows.
 */
@Component
public class TeacherAssignmentPolicy {

    public boolean isAssignmentScoped(EffectiveAuthorizationContext context) {
        return context.role() == UserRoleEnum.TEACHER;
    }
}
