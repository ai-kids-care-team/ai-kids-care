package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.AnnouncementCreateDTO;
import com.ai_kids_care.v1.dto.AnnouncementUpdateDTO;
import com.ai_kids_care.v1.dto.UserCreateDTO;
import com.ai_kids_care.v1.vo.AnnouncementVO;
import com.ai_kids_care.v1.service.AnnouncementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Announcement")
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService service;

    @GetMapping
    public ResponseEntity<Page<AnnouncementVO>> listActiveAnnouncements(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listActiveAnnouncements(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnnouncementVO> getAnnouncement(@PathVariable Long id) {
        return ResponseEntity.ok(service.getAnnouncement(id));
    }

    @PostMapping
    public ResponseEntity<AnnouncementVO> createAnnouncement(@RequestBody AnnouncementCreateDTO createDTO) {

        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAnnouncement(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AnnouncementVO> updateAnnouncement(
            @PathVariable Long id,
            @RequestBody AnnouncementUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateAnnouncement(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAnnouncement(@PathVariable Long id) {
        service.deleteAnnouncement(id);
        return ResponseEntity.noContent().build();
    }
}