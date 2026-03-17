package com.ai_kids_care.dashboard.controller;

import com.ai_kids_care.dashboard.dto.AnnouncementCreateRequest;
import com.ai_kids_care.dashboard.dto.AnnouncementCreateResponse;
import com.ai_kids_care.dashboard.dto.AnnouncementDetailResponse;
import com.ai_kids_care.dashboard.dto.AnnouncementMetaResponse;
import com.ai_kids_care.dashboard.dto.AnnouncementSummaryResponse;
import com.ai_kids_care.dashboard.service.AnnouncementsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
