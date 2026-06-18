package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.AnnouncementCreateDTO;
import com.ai_kids_care.v1.dto.AnnouncementUpdateDTO;
import com.ai_kids_care.v1.entity.Announcement;
import com.ai_kids_care.v1.mapper.AnnouncementMapper;
import com.ai_kids_care.v1.repository.AnnouncementRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContext;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.vo.AnnouncementVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository repository;
    private final UserRepository userRepository;
    private final AnnouncementMapper mapper;


    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PLATFORM_ANNOUNCEMENT_READ)")
    public Page<AnnouncementVO> listActiveAnnouncements(String keyword, Pageable pageable) {
        return repository.listActiveAnnouncements(keyword, pageable)
                .map(mapper::toVO);
    }

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PLATFORM_ANNOUNCEMENT_READ)")
    public AnnouncementVO getAnnouncement(Long id) {
        Announcement announcement = repository
                .findActiveById(id)
                .orElseThrow(() -> new EntityNotFoundException("Announcement not found"));
        announcement.setViewCount(announcement.getViewCount() + 1);
        repository.save(announcement);
        return mapper.toVO(announcement);
    }

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PLATFORM_ANNOUNCEMENT_WRITE)")
    public AnnouncementVO createAnnouncement(AnnouncementCreateDTO createDTO) {
        EffectiveAuthorizationContext context =
                EffectiveAuthorizationContextHolder.require();
        Announcement entity = mapper.toEntity(createDTO);
        entity.setAuthor(userRepository.getReferenceById(context.userId()));
        return mapper.toVO(repository.save(entity));
    }

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PLATFORM_ANNOUNCEMENT_WRITE)")
    public AnnouncementVO updateAnnouncement(Long id, AnnouncementUpdateDTO updateDTO) {
        Announcement entity = repository
                .findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Announcement not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PLATFORM_ANNOUNCEMENT_WRITE)")
    public void deleteAnnouncement(Long id) {
        Announcement entity = repository
                .findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Announcement not found"));
        repository.delete(entity);
    }

}
