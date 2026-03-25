package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.AnnouncementCreateDTO;
import com.ai_kids_care.v1.dto.AnnouncementUpdateDTO;
import com.ai_kids_care.v1.entity.Announcement;
import com.ai_kids_care.v1.mapper.AnnouncementMapper;
import com.ai_kids_care.v1.repository.AnnouncementRepository;
import com.ai_kids_care.v1.vo.AnnouncementVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository repository;
    private final AnnouncementMapper mapper;

//    public Page<AnnouncementVO> listAnnouncements(String keyword, Pageable pageable) {
//        // TODO: filter Announcement by keyword
//        return repository.findAll(pageable).map(mapper::toVO);
//    }

    public Page<AnnouncementVO> listActiveAnnouncements(String keyword, Pageable pageable) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        return repository.listActiveAnnouncements(normalizedKeyword, pageable).map(mapper::toVO);
    }

    public AnnouncementVO getAnnouncement(Long id) {
        Announcement announcement = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Announcement not found"));
        announcement.setViewCount(announcement.getViewCount() + 1);
        repository.save(announcement);
        return mapper.toVO(announcement);
    }

    public AnnouncementVO createAnnouncement(AnnouncementCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public AnnouncementVO updateAnnouncement(Long id, AnnouncementUpdateDTO updateDTO) {
        Announcement entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Announcement not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteAnnouncement(Long id) {
        Announcement entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Announcement not found"));
        repository.delete(entity);
    }
}