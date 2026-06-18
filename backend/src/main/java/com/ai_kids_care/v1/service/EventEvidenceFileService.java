package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.mapper.EventEvidenceFileMapper;
import com.ai_kids_care.v1.repository.EventEvidenceFileRepository;
import com.ai_kids_care.v1.vo.EventEvidenceFileVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventEvidenceFileService {

    private final EventEvidenceFileRepository repository;
    private final EventEvidenceFileMapper mapper;

    @PreAuthorize("denyAll()")
    public Page<EventEvidenceFileVO> listEventEvidenceFiles(String keyword, Pageable pageable) {
        // TODO: filter EventEvidenceFile by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    @PreAuthorize("denyAll()")
    public EventEvidenceFileVO getEventEvidenceFile(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("EventEvidenceFile not found"));
    }
}
