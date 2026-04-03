package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.NotificationCreateDTO;
import com.ai_kids_care.v1.dto.NotificationUpdateDTO;
import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.mapper.NotificationMapper;
import com.ai_kids_care.v1.repository.NotificationRepository;
import com.ai_kids_care.v1.type.NotificationChannelEnum;
import com.ai_kids_care.v1.vo.NotificationVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.pushover.client.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationMapper mapper;
    private final PushoverService pushoverService;

    public Page<NotificationVO> listNotifications(String keyword, Pageable pageable) {
        // TODO: filter Notification by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public NotificationVO getNotification(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));
    }

    public NotificationVO createNotification(NotificationCreateDTO createDTO) {
        NotificationChannelEnum channel = NotificationChannelEnum.from(createDTO.getChannel());

        if (channel == NotificationChannelEnum.PUSH) {
            Status result = pushoverService.sendMessage(
                    "",
                    "",
                    createDTO.getBody(),
                    null,
                    createDTO.getTitle(),
                    "https://github.com/ai-kids-care-team/ai-kids-care",
                    "ai-kids-care",
                    "alien"
            );
        }

        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public NotificationVO updateNotification(Long id, NotificationUpdateDTO updateDTO) {
        Notification entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteNotification(Long id) {
        Notification entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        repository.delete(entity);
    }
}