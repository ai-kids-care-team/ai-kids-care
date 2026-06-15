package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.mapper.CctvCameraMapper;
import com.ai_kids_care.v1.repository.CctvCameraRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.vo.CctvCameraVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CctvCameraService {

    private final CctvCameraRepository repository;
    private final CctvCameraMapper mapper;

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_CAMERA_READ)")
    public Page<CctvCameraVO> listCctvCameras(Long kindergartenId, Pageable pageable) {
        Long effectiveKindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        requireSameKindergarten(kindergartenId, effectiveKindergartenId);
        return repository.findByKindergarten_Id(effectiveKindergartenId, pageable)
                .map(mapper::toVO);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_CAMERA_READ)")
    public CctvCameraVO getCctvCamera(Long id) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository.findByIdAndKindergarten_Id(id, kindergartenId).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("CctvCamera not found"));
    }

    private void requireSameKindergarten(Long requested, Long effective) {
        if (requested != null && !requested.equals(effective)) {
            throw new EntityNotFoundException("CctvCamera not found");
        }
    }
}
