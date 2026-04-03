package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.CameraStreamCreateDTO;
import com.ai_kids_care.v1.dto.CameraStreamUpdateDTO;
import com.ai_kids_care.v1.entity.CameraStream;
import com.ai_kids_care.v1.mapper.CameraStreamMapper;
import com.ai_kids_care.v1.repository.CameraStreamRepository;
import com.ai_kids_care.v1.vo.CameraStreamVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CameraStreamService {

    private final CameraStreamRepository repository;
    private final CameraStreamMapper mapper;

    @Transactional(readOnly = true)
    public Page<CameraStreamVO> listCameraStreams(String keyword, Pageable pageable) {
        // TODO: filter CameraStream by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    @Transactional(readOnly = true)
    public CameraStreamVO getCameraStream(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("CameraStream not found"));
    }

    public CameraStreamVO createCameraStream(CameraStreamCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public CameraStreamVO updateCameraStream(Long id, CameraStreamUpdateDTO updateDTO) {
        CameraStream entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("CameraStream not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteCameraStream(Long id) {
        CameraStream entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("CameraStream not found"));
        repository.delete(entity);
    }
}