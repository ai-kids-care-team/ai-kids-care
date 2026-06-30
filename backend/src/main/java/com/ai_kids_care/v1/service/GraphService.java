package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.repository.GraphRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.vo.graph.ChildGraphVO;
import com.ai_kids_care.v1.vo.graph.TeacherGraphVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Read-only queries over the Neo4j derived relationship graph. tenant-scoped (the active
 * kindergarten comes from {@link EffectiveAuthorizationContextHolder} — the caller never passes
 * {@code kindergartenId}) and staff-only (KINDERGARTEN_ADMIN/TEACHER, {@code GRAPH_READ}). Mirrors
 * the {@code DetectionEventService} pattern: {@code @PreAuthorize} on the service method,
 * {@code requireActiveKindergartenId()} for the tenant predicate written into the Cypher, and a 404
 * (via {@code EntityNotFoundException} from the repository) for any id that is absent for the
 * caller's tenant — cross-tenant existence is hidden.
 *
 * <p>No-PII invariant (INC-003 extended to the read path): the graph holds no PII and these reads map
 * exclusively from Neo4j node/edge values; {@link GraphRepository} must never join back to
 * PostgreSQL to enrich a VO.
 */
@Service
@RequiredArgsConstructor
public class GraphService {

    private final GraphRepository graphRepository;

    // No @Transactional: these reads hit only the Neo4j driver (its own session), never JPA — wrapping
    // them in a PG transaction would needlessly hold a pooled connection across the Neo4j call.
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).GRAPH_READ)")
    public ChildGraphVO getChildGraph(Long childId) {
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return graphRepository.findChildGraph(childId, kindergartenId);
    }

    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).GRAPH_READ)")
    public TeacherGraphVO getTeacherGraph(Long teacherId) {
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return graphRepository.findTeacherGraph(teacherId, kindergartenId);
    }
}
