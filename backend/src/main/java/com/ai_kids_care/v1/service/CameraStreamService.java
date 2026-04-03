package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.CameraStreamCreateDTO;
import com.ai_kids_care.v1.dto.CameraStreamUpdateDTO;
import com.ai_kids_care.v1.entity.CameraStream;
import com.ai_kids_care.v1.mapper.CameraStreamMapper;
import com.ai_kids_care.v1.repository.CameraStreamRepository;
import com.ai_kids_care.v1.security.AesGcmCryptoUtil;
import com.ai_kids_care.v1.vo.CameraStreamVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class CameraStreamService {

    private final CameraStreamRepository repository;
    private final CameraStreamMapper mapper;

    @Value("${camera.streams.encryption.key:${CAMERA_STREAMS_ENC_KEY:GBlTGZwyTbvJ4ToH/F8ogBt/UZVoFe/1waY1EzwImvc=}}")
    private String streamEncryptionKey;

    @Value("${camera.streams.encryption.key-version:${CAMERA_STREAMS_ENC_KEY_VERSION:v1}}")
    private String streamEncryptionKeyVersion;

    public Page<CameraStreamVO> listCameraStreams(String keyword, Pageable pageable) {
        // TODO: filter CameraStream by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public CameraStreamVO getCameraStream(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("CameraStream not found"));
    }

    public CameraStreamVO createCameraStream(CameraStreamCreateDTO createDTO) {
        CameraStream entity = mapper.toEntity(createDTO);
        applyStreamPassword(entity, createDTO.getStreamPassword());
        return mapper.toVO(repository.save(entity));
    }

    public CameraStreamVO updateCameraStream(Long id, CameraStreamUpdateDTO updateDTO) {
        CameraStream entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("CameraStream not found"));
        mapper.updateEntity(updateDTO, entity);
        applyStreamPassword(entity, updateDTO.getStreamPassword());
        return mapper.toVO(repository.save(entity));
    }

    public void deleteCameraStream(Long id) {
        CameraStream entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("CameraStream not found"));
        repository.delete(entity);
    }

    private void applyStreamPassword(CameraStream entity, String streamPassword) {
        if (streamPassword == null) {
            return;
        }

        entity.setCredentialUpdatedAt(OffsetDateTime.now());

        if (streamPassword.isBlank()) {
            entity.setStreamPasswordCiphertext(null);
            entity.setStreamPasswordIv(null);
            entity.setStreamPasswordKeyVersion(null);
            return;
        }

        AesGcmCryptoUtil.EncryptedPayload encryptedPayload = AesGcmCryptoUtil.encrypt(streamPassword, streamEncryptionKey);
        entity.setStreamPasswordCiphertext(encryptedPayload.ciphertext());
        entity.setStreamPasswordIv(encryptedPayload.iv());
        entity.setStreamPasswordKeyVersion(streamEncryptionKeyVersion);
    }
}
