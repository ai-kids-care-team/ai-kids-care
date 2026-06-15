package com.ai_kids_care.v1.security;

import lombok.RequiredArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Proactively revokes every server session for a user (ADR-0019 §6 fast path).
 *
 * The per-request authoritative re-resolution in {@link EffectiveAuthorizationContextFilter}
 * remains the correctness backstop, so even if a delete is delayed or a node misses the
 * message the next request is still rejected. This service makes revocation immediate for
 * logout-all and for future state-change triggers (role revoke, user disable, membership
 * end) once those administrative operations are published — each must call this after the
 * state transaction commits.
 */
@Service
@RequiredArgsConstructor
public class SessionRevocationService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    /**
     * Deletes all indexed sessions whose principal is the given user. Returns the number
     * of sessions revoked. The principal name must match {@link SessionPrincipal#getName()}.
     */
    public int revokeAllForUser(Long userId) {
        Map<String, ? extends Session> sessions =
                sessionRepository.findByPrincipalName("user:" + userId);
        sessions.keySet().forEach(sessionRepository::deleteById);
        return sessions.size();
    }
}
