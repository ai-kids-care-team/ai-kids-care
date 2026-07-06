package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.CameraStreamCreateRequest;
import com.ai_kids_care.v1.dto.CameraStreamUpdateRequest;
import com.ai_kids_care.v1.service.CameraStreamService;
import com.ai_kids_care.v1.vo.CameraStreamVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name="CameraStream")
@RestController
@RequestMapping("/api/v1/camera_streams")
@RequiredArgsConstructor
public class CameraStreamController {

    private final CameraStreamService service;

    @GetMapping
    public ResponseEntity<Page<CameraStreamVO>> listCameraStream(
            @RequestParam(required = false) Long kindergartenId,
            @RequestParam(required = false) Long cameraId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean isPrimary,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listCameraStreams(kindergartenId, cameraId, enabled, isPrimary, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CameraStreamVO> getCameraStream(@PathVariable Long id) {
        return ResponseEntity.ok(service.getCameraStream(id));
    }

    // ADR-0026 Phase 1：摄像头流写端点。授权在 Service 层（@PreAuthorize）完成。

    @PostMapping
    public ResponseEntity<CameraStreamVO> createCameraStream(
            @Valid @RequestBody CameraStreamCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCameraStream(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CameraStreamVO> updateCameraStream(
            @PathVariable Long id,
            @Valid @RequestBody CameraStreamUpdateRequest request) {
        return ResponseEntity.ok(service.updateCameraStream(id, request));
    }
}
