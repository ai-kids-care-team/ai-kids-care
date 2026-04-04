package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.EventReviewCreateDTO;
import com.ai_kids_care.v1.dto.EventReviewUpdateDTO;
import com.ai_kids_care.v1.entity.DetectionEvent;
import com.ai_kids_care.v1.entity.EventReview;
import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.mapper.EventReviewMapper;
import com.ai_kids_care.v1.repository.DetectionEventRepository;
import com.ai_kids_care.v1.repository.EventReviewRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.type.EventStatusEnum;
import com.ai_kids_care.v1.vo.EventReviewVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventReviewService {

    private final EventReviewRepository repository;
    private final EventReviewMapper mapper;
    private final DetectionEventRepository detectionEventRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<EventReviewVO> listEventReviews(
            Long eventId,
            Long userId,
            EventStatusEnum fromStatus,
            EventStatusEnum resultStatus,
            Pageable pageable
    ) {
        return repository.findAllByFilters(eventId, userId, fromStatus, resultStatus, pageable)
                .map(mapper::toVO);
    }

    @Transactional(readOnly = true)
    public EventReviewVO getEventReview(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("EventReview not found"));
    }

    @Transactional(readOnly = true)
    public EventReviewVO getLatestReview(Long eventId) {
        return repository.findTopByDetectionEvents_IdOrderByIdDesc(eventId).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("EventReview not found"));
    }

    @Transactional
    public EventReviewVO createEventReview(EventReviewCreateDTO createDTO) {
        DetectionEvent detectionEvent = detectionEventRepository.findById(createDTO.getEventId())
                .orElseThrow(() -> new EntityNotFoundException("DetectionEvent not found: id=" + createDTO.getEventId()));

        User user = userRepository.findById(createDTO.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found: id=" + createDTO.getUserId()));

        EventStatusEnum fromStatus = EventStatusEnum.valueOf(createDTO.getFromStatus());
        EventStatusEnum resultStatus = createDTO.getResultStatus() != null && !createDTO.getResultStatus().isBlank()
                ? EventStatusEnum.valueOf(createDTO.getResultStatus())
                : null;

        EventReview review = EventReview.builder()
                .detectionEvents(detectionEvent)
                .kindergarten(detectionEvent.getKindergarten())
                .user(user)
                .fromStatus(fromStatus)
                .resultStatus(resultStatus)
                .comment(createDTO.getComment())
                .build();

        EventReview saved = repository.save(review);
        return mapper.toVO(saved);
    }

    @Transactional
    public EventReviewVO updateEventReview(Long id, EventReviewUpdateDTO updateDTO) {
        EventReview entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("EventReview not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    @Transactional
    public void deleteEventReview(Long id) {
        EventReview entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("EventReview not found"));
        repository.delete(entity);
    }
}
