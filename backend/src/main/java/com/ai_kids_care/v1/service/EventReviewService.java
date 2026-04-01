package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.EventReviewCreateDTO;
import com.ai_kids_care.v1.dto.EventReviewUpdateDTO;
import com.ai_kids_care.v1.entity.EventReview;
import com.ai_kids_care.v1.mapper.EventReviewMapper;
import com.ai_kids_care.v1.repository.EventReviewRepository;
import com.ai_kids_care.v1.vo.EventReviewVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventReviewService {

    private final EventReviewRepository repository;
    private final EventReviewMapper mapper;

    public Page<EventReviewVO> listEventReviews(String keyword, Pageable pageable) {
        // TODO: filter EventReview by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public EventReviewVO getEventReview(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("EventReview not found"));
    }

    public EventReviewVO getLatestReview(Long eventId) {
        return repository.findTopByDetectionEvents_IdOrderByIdDesc(eventId).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("EventReview not found"));
    }

    public List<EventReviewVO> listReviewsByEventId(Long eventId) {
        return repository.findAllByDetectionEvents_IdOrderByIdAsc(eventId)
                .stream()
                .map(mapper::toVO)
                .toList();
    }

    public EventReviewVO createEventReview(EventReviewCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public EventReviewVO updateEventReview(Long id, EventReviewUpdateDTO updateDTO) {
        EventReview entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("EventReview not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteEventReview(Long id) {
        EventReview entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("EventReview not found"));
        repository.delete(entity);
    }
}