package com.ai_kids_care.v1.security;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.time.OffsetDateTime;

public record SessionPrincipal(
        Long userId,
        Long roleAssignmentId,
        Long membershipId,
        OffsetDateTime establishedAt
) implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return "user:" + userId;
    }
}
