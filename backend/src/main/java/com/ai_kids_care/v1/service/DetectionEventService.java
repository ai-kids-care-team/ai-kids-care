package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.DetectionEventCreateDTO;
import com.ai_kids_care.v1.dto.DetectionEventUpdateDTO;
import com.ai_kids_care.v1.entity.DetectionEvent;
import com.ai_kids_care.v1.mapper.DetectionEventMapper;
import com.ai_kids_care.v1.repository.DetectionEventRepository;
import com.ai_kids_care.v1.vo.DetectionEventVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DetectionEventService {

    private final DetectionEventRepository repository;
    private final DetectionEventMapper mapper;

    public Page<DetectionEventVO> listDetectionEvents(String keyword, Pageable pageable) {
        // TODO: filter DetectionEvent by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public DetectionEventVO getDetectionEvent(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("DetectionEvent not found"));
    }

    public DetectionEventVO createDetectionEvent(DetectionEventCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public DetectionEventVO updateDetectionEvent(Long id, DetectionEventUpdateDTO updateDTO) {
        DetectionEvent entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("DetectionEvent not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteDetectionEvent(Long id) {
        DetectionEvent entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("DetectionEvent not found"));
        repository.delete(entity);
    }
}