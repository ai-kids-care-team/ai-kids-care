package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.DeviceTokenCreateDTO;
import com.ai_kids_care.v1.dto.DeviceTokenUpdateDTO;
import com.ai_kids_care.v1.vo.DeviceTokenVO;
import com.ai_kids_care.v1.service.DeviceTokenService;
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

@Tag(name="DeviceToken")
@RestController
@RequestMapping("/api/v1/device_tokens")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService service;

    @GetMapping
    public ResponseEntity<Page<DeviceTokenVO>> listDeviceToken(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listDeviceTokens(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceTokenVO> getDeviceToken(@PathVariable Long id) {
        return ResponseEntity.ok(service.getDeviceToken(id));
    }

    @PostMapping
    public ResponseEntity<DeviceTokenVO> createDeviceToken(@RequestBody @Valid DeviceTokenCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDeviceToken(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeviceTokenVO> updateDeviceToken(
            @PathVariable Long id,
            @RequestBody @Valid DeviceTokenUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateDeviceToken(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDeviceToken(@PathVariable Long id) {
        service.deleteDeviceToken(id);
        return ResponseEntity.noContent().build();
    }
}