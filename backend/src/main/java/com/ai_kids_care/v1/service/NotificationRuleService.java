package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.NotificationRuleCreateDTO;
import com.ai_kids_care.v1.dto.NotificationRuleUpdateDTO;
import com.ai_kids_care.v1.entity.NotificationRule;
import com.ai_kids_care.v1.mapper.NotificationRuleMapper;
import com.ai_kids_care.v1.repository.NotificationRuleRepository;
import com.ai_kids_care.v1.type.EventTypeEnum;
import com.ai_kids_care.v1.type.NotificationTargetType;
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

    public Page<NotificationRuleVO> listNotificationRules(
            Long userId,
            NotificationTargetType targetType,
            Long targetId,
            EventTypeEnum eventType,
            Integer minSeverity,
            Boolean enabled,
            Pageable pageable
    ) {
        return repository.searchByFilters(
                userId,
                targetType,
                targetId,
                eventType,
                minSeverity,
                enabled,
                pageable
        ).map(mapper::toVO);
    }

    public NotificationRuleVO getNotificationRule(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("NotificationRule not found"));
    }

    public NotificationRuleVO createNotificationRule(NotificationRuleCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public NotificationRuleVO updateNotificationRule(Long id, NotificationRuleUpdateDTO updateDTO) {
        NotificationRule entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("NotificationRule not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteNotificationRule(Long id) {
        NotificationRule entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("NotificationRule not found"));
        repository.delete(entity);
    }
}