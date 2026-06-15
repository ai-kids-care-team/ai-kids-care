package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.mapper.DetectionSessionMapper;
import com.ai_kids_care.v1.repository.DetectionSessionRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.vo.DetectionSessionVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DetectionSessionService {

    private final DetectionSessionRepository repository;
    private final DetectionSessionMapper mapper;

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_SURVEILLANCE_READ)")
    public Page<DetectionSessionVO> listDetectionSessions(String keyword, Pageable pageable) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository
                .findAllByCameraStreams_CctvCameras_Kindergarten_Id(
                        kindergartenId,
                        pageable)
                .map(mapper::toVO);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_SURVEILLANCE_READ)")
    public DetectionSessionVO getDetectionSession(Long id) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository
                .findByIdAndCameraStreams_CctvCameras_Kindergarten_Id(
                        id,
                        kindergartenId)
                .map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("DetectionSession not found"));
    }
}
