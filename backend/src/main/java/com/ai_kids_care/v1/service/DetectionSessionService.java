package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.DetectionSessionCreateDTO;
import com.ai_kids_care.v1.dto.DetectionSessionUpdateDTO;
import com.ai_kids_care.v1.entity.DetectionSession;
import com.ai_kids_care.v1.mapper.DetectionSessionMapper;
import com.ai_kids_care.v1.repository.DetectionSessionRepository;
import com.ai_kids_care.v1.vo.DetectionSessionVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DetectionSessionService {

    private final DetectionSessionRepository repository;
    private final DetectionSessionMapper mapper;

    public Page<DetectionSessionVO> listDetectionSessions(String keyword, Pageable pageable) {
        // TODO: filter DetectionSession by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public DetectionSessionVO getDetectionSession(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("DetectionSession not found"));
    }

    public DetectionSessionVO createDetectionSession(DetectionSessionCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public DetectionSessionVO updateDetectionSession(Long id, DetectionSessionUpdateDTO updateDTO) {
        DetectionSession entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("DetectionSession not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteDetectionSession(Long id) {
        DetectionSession entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("DetectionSession not found"));
        repository.delete(entity);
    }
}