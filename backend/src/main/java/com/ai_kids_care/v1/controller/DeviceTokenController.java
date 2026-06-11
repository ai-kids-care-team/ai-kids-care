package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.service.DeviceTokenService;
import com.ai_kids_care.v1.vo.DeviceTokenVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDeviceToken(@PathVariable Long id) {
        service.deleteDeviceToken(id);
        return ResponseEntity.noContent().build();
    }
}
