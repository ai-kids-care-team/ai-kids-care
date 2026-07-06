package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.NotificationPreferenceUpdateDTO;
import com.ai_kids_care.v1.entity.NotificationRule;
import com.ai_kids_care.v1.repository.KindergartenRepository;
import com.ai_kids_care.v1.repository.NotificationRuleRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.type.NotificationTargetType;
import com.ai_kids_care.v1.vo.NotificationPreferenceVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * UX-08: self-service management of the caller's own notification preference — a canonical
 * {@code (kindergarten_id, user_id, target_type=KINDERGARTEN)} row in the existing
 * {@code notification_rules} table (no schema migration; see design.md). Coarse gate:
 * {@code NOTIFICATION_PREFERENCE_MANAGE} (any authenticated user, mirrors
 * {@link PushSubscriptionService}). Fine-grained scope: {@code userId}/{@code kindergartenId} are
 * always derived from the session identity (never trusted from the request), and every query
 * carries both predicates — cross-user/cross-tenant rows are simply never found (hidden, no 403).
 *
 * {@link #findCanonical(Long, Long)} is a plain read used by {@link GuardianNotificationService}
 * on its {@code @Async @TransactionalEventListener(AFTER_COMMIT)} delivery path, which runs with
 * no session identity / ThreadLocal auth context — it deliberately has no {@code @PreAuthorize}
 * and takes both ids explicitly, and returns a detached {@link NotificationPreferenceSnapshot}
 * (plain scalars, not the entity) so no lazy-proxy can leak across the async thread boundary.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private static final NotificationTargetType CANONICAL_TARGET_TYPE = NotificationTargetType.KINDERGARTEN;
    private static final int DEFAULT_MIN_SEVERITY = 0; // match-all; this slice's delivery path doesn't read it

    private final NotificationRuleRepository repository;
    private final KindergartenRepository kindergartenRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /** A defensive, detached snapshot of the canonical row's fields — safe to read post-commit/async. */
    public record NotificationPreferenceSnapshot(boolean enabled, String quietHoursJson) {
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).NOTIFICATION_PREFERENCE_MANAGE)")
    public NotificationPreferenceVO getMine() {
        Long userId = EffectiveAuthorizationContextHolder.require().userId();
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository.findCanonical(kindergartenId, userId, CANONICAL_TARGET_TYPE)
                .map(this::toVO)
                .orElseGet(() -> new NotificationPreferenceVO(true, null, null));
    }

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).NOTIFICATION_PREFERENCE_MANAGE)")
    public NotificationPreferenceVO upsertMine(NotificationPreferenceUpdateDTO dto) {
        Long userId = EffectiveAuthorizationContextHolder.require().userId();
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();

        String quietHoursJson = buildQuietHoursJson(dto.getQuietHoursStart(), dto.getQuietHoursEnd());

        NotificationRule rule = repository.findCanonical(kindergartenId, userId, CANONICAL_TARGET_TYPE)
                .orElseGet(() -> NotificationRule.builder()
                        .kindergarten(kindergartenRepository.getReferenceById(kindergartenId))
                        .user(userRepository.getReferenceById(userId))
                        .targetType(CANONICAL_TARGET_TYPE)
                        .targetId(kindergartenId)
                        .minSeverity(DEFAULT_MIN_SEVERITY)
                        .eventType(null)
                        .build());
        rule.setEnabled(dto.getEnabled());
        rule.setQuietHoursJson(quietHoursJson);
        return toVO(repository.save(rule));
    }

    /**
     * Read-only lookup for the delivery path (no {@code @PreAuthorize} — see class javadoc).
     * Returns {@code null} when no canonical row exists (caller falls back to kindergarten-level).
     */
    @Transactional(readOnly = true)
    public NotificationPreferenceSnapshot findCanonical(Long kindergartenId, Long userId) {
        return repository.findCanonical(kindergartenId, userId, CANONICAL_TARGET_TYPE)
                .map(r -> new NotificationPreferenceSnapshot(Boolean.TRUE.equals(r.getEnabled()), r.getQuietHoursJson()))
                .orElse(null);
    }

    private NotificationPreferenceVO toVO(NotificationRule rule) {
        JsonNode node = parseQuietHours(rule.getQuietHoursJson());
        String start = node == null ? null : textOrNull(node.get("start"));
        String end = node == null ? null : textOrNull(node.get("end"));
        return new NotificationPreferenceVO(Boolean.TRUE.equals(rule.getEnabled()), start, end);
    }

    private JsonNode parseQuietHours(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return null; // malformed persisted value — treat as unset rather than 500
        }
    }

    private String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    /** Both null = clear quiet hours (store null); both set = store {"start","end"}; one-sided = 400. */
    private String buildQuietHoursJson(String start, String end) {
        boolean hasStart = StringUtils.hasText(start);
        boolean hasEnd = StringUtils.hasText(end);
        if (!hasStart && !hasEnd) {
            return null;
        }
        if (hasStart != hasEnd) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "quietHoursStart and quietHoursEnd must both be set or both be null");
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("start", start);
        node.put("end", end);
        return node.toString();
    }
}
