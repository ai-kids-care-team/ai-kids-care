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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationMapper mapper;
    private final NotificationSmsDispatchService notificationSmsDispatchService;

    public Page<NotificationVO> listNotifications(String keyword, Pageable pageable) {
        return repository.search(keyword, pageable).map(mapper::toVO);
    }

    public NotificationVO getNotification(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));
    }

    public NotificationVO createNotification(NotificationCreateDTO createDTO) {
        NotificationChannelEnum channel = createDTO.getChannel();
        Notification entity = mapper.toEntity(createDTO);
        entity.setChannel(channel);

        String dedupeKey = resolveDedupeKey(createDTO, channel);
        entity.setDedupeKey(dedupeKey);
        Long kindergartenId = entity.getKindergarten() != null ? entity.getKindergarten().getId() : null;
        if (kindergartenId == null) {
            throw new IllegalArgumentException("kindergartenId can not be null");
        }

        return repository.findByKindergarten_IdAndDedupeKey(kindergartenId, dedupeKey)
                .map(mapper::toVO)
                .orElseGet(() -> mapper.toVO(repository.save(entity)));
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

    public int dispatchPendingSmsNotifications() {
        return notificationSmsDispatchService.dispatchPendingSmsNotifications();
    }

    private String resolveDedupeKey(NotificationCreateDTO createDTO, NotificationChannelEnum channel) {
        String dedupeKey = createDTO.getDedupeKey();
        if (dedupeKey != null && !dedupeKey.trim().isEmpty()) {
            return dedupeKey.trim();
        }

        String raw = String.join("|",
                String.valueOf(createDTO.getKindergartenId()),
                String.valueOf(createDTO.getEventId()),
                String.valueOf(createDTO.getRecipientUserId()),
                channel.name(),
                createDTO.getTitle() == null ? "" : createDTO.getTitle().trim(),
                createDTO.getBody() == null ? "" : createDTO.getBody().trim()
        );

        return sha256(raw);
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
