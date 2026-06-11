package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.CameraStream;
import com.ai_kids_care.v1.mapper.CameraStreamMapper;
import com.ai_kids_care.v1.repository.CameraStreamRepository;
import com.ai_kids_care.v1.vo.CameraStreamVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CameraStreamService {

    private final CameraStreamRepository repository;
    private final CameraStreamMapper mapper;

    @Transactional(readOnly = true)
    public Page<CameraStreamVO> listCameraStreams(
            Long kindergartenId,
            Long cameraId,
            Boolean enabled,
            Boolean isPrimary,
            Pageable pageable
    ) {
        return repository.findAllByFilters(kindergartenId, cameraId, enabled, isPrimary, pageable)
                .map(mapper::toVO);
    }

    @Transactional(readOnly = true)
    public CameraStreamVO getCameraStream(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("CameraStream not found"));
    }

    public void deleteCameraStream(Long id) {
        CameraStream entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("CameraStream not found"));
        repository.delete(entity);
    }
}
