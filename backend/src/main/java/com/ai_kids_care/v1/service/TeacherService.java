package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.mapper.TeacherMapper;
import com.ai_kids_care.v1.repository.TeacherRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.vo.TeacherVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final TeacherRepository repository;
    private final TeacherMapper mapper;

    /**
     * GT-1 / SEC-07: tenant-scoped teacher roster read. Coarse {@code TENANT_S2_READ}
     * gate (KINDERGARTEN_ADMIN + TEACHER) plus a mandatory active-kindergarten filter,
     * so both roles see only same-kindergarten colleagues. No assignment-level narrowing
     * is applied — viewing the staff roster of one's own kindergarten is legitimate for a
     * teacher (mirrors Class/Room read pattern but without the assignment scope).
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_S2_READ)")
    public Page<TeacherVO> listTeachers(String keyword, Long userId, Pageable pageable) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (hasKeyword && userId != null) {
            return repository
                    .findByNameContainsAndUserIdAndKindergarten_Id(
                            keyword, userId, kindergartenId, pageable)
                    .map(mapper::toVO);
        }

        if (hasKeyword) {
            return repository
                    .findByNameContainsAndKindergarten_Id(keyword, kindergartenId, pageable)
                    .map(mapper::toVO);
        }

        if (userId != null) {
            return repository
                    .findByUserIdAndKindergarten_Id(userId, kindergartenId, pageable)
                    .map(mapper::toVO);
        }

        return repository.findAllByKindergarten_Id(kindergartenId, pageable).map(mapper::toVO);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_S2_READ)")
    public TeacherVO getTeacher(Long id) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository.findByIdAndKindergarten_Id(id, kindergartenId).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Teacher not found"));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_S2_READ)")
    public TeacherVO getTeacherByUserId(Long userId) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository.findByUserIdAndKindergarten_Id(userId, kindergartenId).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Teacher not found"));
    }
}
