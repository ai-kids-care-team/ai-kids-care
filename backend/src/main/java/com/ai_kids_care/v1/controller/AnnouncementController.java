package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.AnnouncementCreateDTO;
import com.ai_kids_care.v1.dto.AnnouncementUpdateDTO;
import com.ai_kids_care.v1.vo.AnnouncementMetaVO;
import com.ai_kids_care.v1.vo.AnnouncementSummaryVO;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name="Announcement")
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService service;

    @GetMapping
    public ResponseEntity<Page<AnnouncementSummaryVO>> listAnnouncements(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 5) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listSummaryAnnouncements(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnnouncementSummaryVO> getAnnouncement(@PathVariable Long id) {
        return ResponseEntity.ok(service.getAnnouncement(id));
    }

    @GetMapping("/{id}/edit")
    public ResponseEntity<AnnouncementVO> getAnnouncementForEdit(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String loginId = extractLoginId(authentication);
        return ResponseEntity.ok(service.getAnnouncementForEdit(loginId, id));
    }

    @GetMapping("/meta")
    public ResponseEntity<AnnouncementMetaVO> getAnnouncementsMeta(
            Authentication authentication
    ) {
        String loginId = extractLoginId(authentication);
        return ResponseEntity.ok(service.getMeta(loginId));
    }

    private String extractLoginId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal != null && "anonymousUser".equals(principal.toString())) {
            return null;
        }
        return authentication.getName();
    }

    @PostMapping
    public ResponseEntity<AnnouncementVO> createAnnouncement(
            Authentication authentication,
            @RequestBody AnnouncementCreateDTO createDTO
    ) {
        String loginId = extractLoginId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAnnouncement(createDTO, loginId));
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