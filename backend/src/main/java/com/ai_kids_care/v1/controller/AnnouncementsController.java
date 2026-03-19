package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.*;
import com.ai_kids_care.v1.service.AnnouncementsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementsController {

    private final AnnouncementsService announcementsService;

    @GetMapping
    public ResponseEntity<List<AnnouncementSummaryResponse>> getAnnouncements() {
        return ResponseEntity.ok(announcementsService.getActiveAnnouncements());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnnouncementDetailResponse> getAnnouncementDetail(@PathVariable Long id) {
        return ResponseEntity.ok(announcementsService.getAnnouncementDetail(id));
    }

    @GetMapping("/meta")
    public ResponseEntity<AnnouncementMetaResponse> getAnnouncementsMeta(
            Authentication authentication
    ) {
        String loginId = extractLoginId(authentication);
        return ResponseEntity.ok(announcementsService.getMeta(loginId));
    }

    @PostMapping
    public ResponseEntity<AnnouncementCreateResponse> createAnnouncement(
            @RequestBody AnnouncementCreateRequest request,
            Authentication authentication
    ) {
        String loginId = extractLoginId(authentication);
        return ResponseEntity.ok(announcementsService.createAnnouncement(loginId, request));
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
}
