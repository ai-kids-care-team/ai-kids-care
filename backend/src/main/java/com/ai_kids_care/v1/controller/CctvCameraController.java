package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.service.CctvCameraService;
import com.ai_kids_care.v1.vo.CctvCameraVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name="CctvCamera")
@RestController
@RequestMapping("/api/v1/cctv_cameras")
@RequiredArgsConstructor
public class CctvCameraController {

    private final CctvCameraService service;

    @GetMapping
    public ResponseEntity<Page<CctvCameraVO>> listCctvCamera(
            @RequestParam Long kindergartenId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listCctvCameras(kindergartenId, pageable));
    }
    @GetMapping("/{id}")
    public ResponseEntity<CctvCameraVO> getCctvCamera(@PathVariable Long id) {
        return ResponseEntity.ok(service.getCctvCamera(id));
    }
}
