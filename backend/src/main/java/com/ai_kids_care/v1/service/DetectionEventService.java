package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.mapper.DetectionEventMapper;
import com.ai_kids_care.v1.repository.DetectionEventRepository;
import com.ai_kids_care.v1.vo.DetectionEventVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DetectionEventService {

    private final DetectionEventRepository repository;
    private final DetectionEventMapper mapper;

    @Transactional(readOnly = true)
    public Page<DetectionEventVO> listDetectionEvents(Long kindergartenId, String keyword, Pageable pageable) {
        // TODO: filter DetectionEvent by keyword
        return repository.findByKindergarten_Id(kindergartenId, pageable).map(mapper::toVO);
    }

    @Transactional(readOnly = true)
    public DetectionEventVO getDetectionEvent(Long id, Long kindergartenId) {
        return repository.findByIdAndKindergarten_Id(id, kindergartenId).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("DetectionEvent not found"));
    }
}
