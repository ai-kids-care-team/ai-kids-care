package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.CctvCameraCreateDTO;
import com.ai_kids_care.v1.dto.CctvCameraUpdateDTO;
import com.ai_kids_care.v1.entity.CctvCamera;
import com.ai_kids_care.v1.mapper.CctvCameraMapper;
import com.ai_kids_care.v1.repository.CctvCameraRepository;
import com.ai_kids_care.v1.vo.CctvCameraVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CctvCameraService {

    private final CctvCameraRepository repository;
    private final CctvCameraMapper mapper;

    public Page<CctvCameraVO> listCctvCameras(Long kindergartenId, Pageable pageable) {
        // filter CctvCamera by kindergartenId
        return repository.findByKindergarten_Id(kindergartenId ,pageable).map(mapper::toVO);
    }

    public CctvCameraVO getCctvCamera(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("CctvCamera not found"));
    }

    public CctvCameraVO createCctvCamera(CctvCameraCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public CctvCameraVO updateCctvCamera(Long id, CctvCameraUpdateDTO updateDTO) {
        CctvCamera entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("CctvCamera not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteCctvCamera(Long id) {
        CctvCamera entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("CctvCamera not found"));
        repository.delete(entity);
    }
}