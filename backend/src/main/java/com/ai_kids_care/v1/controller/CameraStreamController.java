package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.CameraStreamCreateDTO;
import com.ai_kids_care.v1.dto.CameraStreamUpdateDTO;
import com.ai_kids_care.v1.vo.CameraStreamVO;
import com.ai_kids_care.v1.service.CameraStreamService;
import io.swagger.v3.oas.annotations.tags.Tag;
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
            @RequestParam Long kindergartenId,
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

    @PostMapping
    public ResponseEntity<CameraStreamVO> createCameraStream(@RequestBody CameraStreamCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCameraStream(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CameraStreamVO> updateCameraStream(
            @PathVariable Long id,
            @RequestBody CameraStreamUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateCameraStream(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCameraStream(@PathVariable Long id) {
        service.deleteCameraStream(id);
        return ResponseEntity.noContent().build();
    }
}