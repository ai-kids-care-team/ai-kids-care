package com.ai_kids_care.v1.security;

import com.ai_kids_care.v1.vo.AuthSessionVO;

public record AuthenticatedSession(
        SessionPrincipal principal,
        AuthSessionVO profile
) {
}
