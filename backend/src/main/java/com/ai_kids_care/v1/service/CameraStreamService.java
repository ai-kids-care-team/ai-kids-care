package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.mapper.CameraStreamMapper;
import com.ai_kids_care.v1.repository.CameraStreamRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.vo.CameraStreamVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CameraStreamService {

    private final CameraStreamRepository repository;
    private final CameraStreamMapper mapper;

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_CAMERA_READ)")
    public Page<CameraStreamVO> listCameraStreams(
            Long kindergartenId,
            Long cameraId,
            Boolean enabled,
            Boolean isPrimary,
            Pageable pageable
    ) {
        Long effectiveKindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        requireSameKindergarten(kindergartenId, effectiveKindergartenId);
        return repository.findAllByFilters(
                        effectiveKindergartenId,
                        cameraId,
                        enabled,
                        isPrimary,
                        pageable)
                .map(mapper::toVO);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_CAMERA_READ)")
    public CameraStreamVO getCameraStream(Long id) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository
                .findByIdAndCctvCameras_Kindergarten_Id(id, kindergartenId)
                .map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("CameraStream not found"));
    }

    private void requireSameKindergarten(Long requested, Long effective) {
        if (requested != null && !requested.equals(effective)) {
            throw new EntityNotFoundException("CameraStream not found");
        }
    }
}
