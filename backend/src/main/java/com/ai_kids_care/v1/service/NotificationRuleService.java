package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.mapper.NotificationRuleMapper;
import com.ai_kids_care.v1.repository.NotificationRuleRepository;
import com.ai_kids_care.v1.vo.NotificationRuleVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationRuleService {

    private final NotificationRuleRepository repository;
    private final NotificationRuleMapper mapper;

    public Page<NotificationRuleVO> listNotificationRules(String keyword, Pageable pageable) {
        // TODO: filter NotificationRule by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public NotificationRuleVO getNotificationRule(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("NotificationRule not found"));
    }
}
