package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.EventReviewCreateDTO;
import com.ai_kids_care.v1.dto.EventReviewUpdateDTO;
import com.ai_kids_care.v1.entity.EventReview;
import com.ai_kids_care.v1.entity.DetectionEvent;
import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.mapper.EventReviewMapper;
import com.ai_kids_care.v1.repository.EventReviewRepository;
import com.ai_kids_care.v1.repository.DetectionEventRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.type.EventStatusEnum;
import com.ai_kids_care.v1.vo.EventReviewVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventReviewService {

    private final EventReviewRepository repository;
    private final EventReviewMapper mapper;
    private final DetectionEventRepository detectionEventRepository;
    private final UserRepository userRepository;

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

    @Transactional
    public EventReviewVO createEventReview(EventReviewCreateDTO createDTO) {
        // 1) 연관 엔티티를 DB 에서 조회 (transient 객체 금지)
        DetectionEvent detectionEvent = detectionEventRepository.findById(createDTO.getEventId())
                .orElseThrow(() -> new EntityNotFoundException("DetectionEvent not found: id=" + createDTO.getEventId()));

        User user = userRepository.findById(createDTO.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found: id=" + createDTO.getUserId()));

        // 2) 문자열 상태값을 enum 으로 변환
        EventStatusEnum fromStatus = EventStatusEnum.valueOf(createDTO.getFromStatus());
        EventStatusEnum resultStatus = createDTO.getResultStatus() != null
                ? EventStatusEnum.valueOf(createDTO.getResultStatus())
                : null;

        // 3) EventReview 엔티티 생성
        EventReview review = new EventReview();
        review.setDetectionEvents(detectionEvent);
        review.setKindergarten(detectionEvent.getKindergarten());
        review.setUser(user);
        review.setFromStatus(fromStatus);
        review.setResultStatus(resultStatus);
        review.setComment(createDTO.getComment());
        review.setCreatedAt(OffsetDateTime.now());

        EventReview saved = repository.save(review);
        return mapper.toVO(saved);
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