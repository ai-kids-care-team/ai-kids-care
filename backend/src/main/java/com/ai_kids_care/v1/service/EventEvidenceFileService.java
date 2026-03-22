package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.EventEvidenceFileCreateDTO;
import com.ai_kids_care.v1.dto.EventEvidenceFileUpdateDTO;
import com.ai_kids_care.v1.entity.EventEvidenceFile;
import com.ai_kids_care.v1.mapper.EventEvidenceFileMapper;
import com.ai_kids_care.v1.repository.EventEvidenceFileRepository;
import com.ai_kids_care.v1.vo.EventEvidenceFileVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventEvidenceFileService {

    private final EventEvidenceFileRepository repository;
    private final EventEvidenceFileMapper mapper;

    public Page<EventEvidenceFileVO> listEventEvidenceFiles(String keyword, Pageable pageable) {
        // TODO: filter EventEvidenceFile by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public EventEvidenceFileVO getEventEvidenceFile(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("EventEvidenceFile not found"));
    }

    public EventEvidenceFileVO createEventEvidenceFile(EventEvidenceFileCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public EventEvidenceFileVO updateEventEvidenceFile(Long id, EventEvidenceFileUpdateDTO updateDTO) {
        EventEvidenceFile entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("EventEvidenceFile not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteEventEvidenceFile(Long id) {
        EventEvidenceFile entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("EventEvidenceFile not found"));
        repository.delete(entity);
    }
}