package com.ai_kids_care.v1.service;

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
}
