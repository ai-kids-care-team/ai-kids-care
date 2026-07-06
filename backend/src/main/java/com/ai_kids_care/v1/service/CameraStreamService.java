package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.config.CameraStreamCryptoConfig;
import com.ai_kids_care.v1.dto.CameraStreamCreateRequest;
import com.ai_kids_care.v1.dto.CameraStreamUpdateRequest;
import com.ai_kids_care.v1.entity.CameraStream;
import com.ai_kids_care.v1.entity.CctvCamera;
import com.ai_kids_care.v1.mapper.CameraStreamMapper;
import com.ai_kids_care.v1.repository.CameraStreamRepository;
import com.ai_kids_care.v1.repository.CctvCameraRepository;
import com.ai_kids_care.v1.security.AesGcmCryptoUtil;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.vo.CameraStreamVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Tenant-scoped {@code @PreAuthorize} CRUD for camera streams (list/get/create/update). AI-internal
 * operations (credential decrypt, active-stream listing, Claim/Lease) live in
 * {@link CameraStreamInternalService} — split out by harden-claim-lease-internal C2 (ARC-01) so
 * this class only ever holds session-authorized, kindergarten-scoped methods; see that class's
 * javadoc for the rationale.
 */
@Service
@RequiredArgsConstructor
public class CameraStreamService {

    private final CameraStreamRepository repository;
    private final CameraStreamMapper mapper;
    private final CctvCameraRepository cctvCameraRepository;
    private final CameraStreamCryptoConfig cryptoConfig;

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_SURVEILLANCE_READ)")
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
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_SURVEILLANCE_READ)")
    public CameraStreamVO getCameraStream(Long id) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository
                .findByIdAndCctvCameras_Kindergarten_Id(id, kindergartenId)
                .map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("CameraStream not found"));
    }

    // ADR-0026 Phase 1：摄像头流写方法（create/update）。
    // 授权粗粒度门由 @PreAuthorize 在 Service 层完成；细粒度 tenant 隔离由 repository 查询强制。

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_SURVEILLANCE_WRITE)")
    public CameraStreamVO createCameraStream(CameraStreamCreateRequest request) {
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        // tenant-scoped camera 查找（防止跨园关联攻击）
        CctvCamera camera = cctvCameraRepository
                .findByIdAndKindergarten_Id(request.getCameraId(), kindergartenId)
                .orElseThrow(() -> new EntityNotFoundException("CctvCamera not found"));

        CameraStream entity = CameraStream.builder()
                .cctvCameras(camera)
                .streamType(request.getStreamType())
                .sourceUrl(request.getSourceUrl())
                .streamUser(request.getStreamUser())
                .sourceProtocol(request.getSourceProtocol())
                .playbackUrl(request.getPlaybackUrl())
                .playbackProtocol(request.getPlaybackProtocol())
                .fps(request.getFps())
                .resolution(request.getResolution())
                .isPrimary(request.getIsPrimary())
                .enabled(request.getEnabled())
                .build();

        encryptPasswordIfPresent(request.getStreamPassword(), entity);

        return mapper.toVO(repository.save(entity));
    }

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_SURVEILLANCE_WRITE)")
    public CameraStreamVO updateCameraStream(Long id, CameraStreamUpdateRequest request) {
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        CameraStream entity = repository
                .findByIdAndCctvCameras_Kindergarten_Id(id, kindergartenId)
                .orElseThrow(() -> new EntityNotFoundException("CameraStream not found"));

        // 仅更新非 null 字段（partial update 语义）
        if (request.getStreamType() != null)       entity.setStreamType(request.getStreamType());
        if (request.getSourceUrl() != null)        entity.setSourceUrl(request.getSourceUrl());
        if (request.getStreamUser() != null)       entity.setStreamUser(request.getStreamUser());
        if (request.getSourceProtocol() != null)   entity.setSourceProtocol(request.getSourceProtocol());
        if (request.getPlaybackUrl() != null)      entity.setPlaybackUrl(request.getPlaybackUrl());
        if (request.getPlaybackProtocol() != null) entity.setPlaybackProtocol(request.getPlaybackProtocol());
        if (request.getFps() != null)              entity.setFps(request.getFps());
        if (request.getResolution() != null)       entity.setResolution(request.getResolution());
        if (request.getIsPrimary() != null)        entity.setIsPrimary(request.getIsPrimary());
        if (request.getEnabled() != null)          entity.setEnabled(request.getEnabled());

        encryptPasswordIfPresent(request.getStreamPassword(), entity);

        return mapper.toVO(repository.save(entity));
    }

    /**
     * 若 streamPassword 非空则加密并写入 entity；密码明文不记录到日志。
     */
    private void encryptPasswordIfPresent(String streamPassword, CameraStream entity) {
        if (streamPassword != null && !streamPassword.isBlank()) {
            AesGcmCryptoUtil.EncryptedPayload payload =
                    AesGcmCryptoUtil.encrypt(streamPassword, cryptoConfig.activeKey());
            entity.setStreamPasswordCiphertext(payload.ciphertext());
            entity.setStreamPasswordIv(payload.iv());
            entity.setStreamPasswordKeyVersion(cryptoConfig.getCurrentVersion());
            entity.setCredentialUpdatedAt(OffsetDateTime.now());
        }
    }

    private void requireSameKindergarten(Long requested, Long effective) {
        if (requested != null && !requested.equals(effective)) {
            throw new EntityNotFoundException("CameraStream not found");
        }
    }
}
