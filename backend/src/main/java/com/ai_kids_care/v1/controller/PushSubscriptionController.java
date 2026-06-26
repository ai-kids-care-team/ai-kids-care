package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.PushSubscriptionRegisterDTO;
import com.ai_kids_care.v1.dto.PushSubscriptionUpdateDTO;
import com.ai_kids_care.v1.service.PushSubscriptionService;
import com.ai_kids_care.v1.vo.PushSubscriptionVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Push subscription self-service management API. Authenticated users manage their OWN Pushover
 * delivery subscriptions; authorization is enforced at the service layer (@PreAuthorize coarse
 * gate + user-scoped queries). Read responses never expose the delivery {@code address}.
 */
@Tag(name = "PushSubscription")
@RestController
@RequestMapping("/api/v1/push_subscriptions")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionService service;

    @PostMapping
    public ResponseEntity<PushSubscriptionVO> register(@Valid @RequestBody PushSubscriptionRegisterDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(dto));
    }

    @GetMapping
    public ResponseEntity<List<PushSubscriptionVO>> listOwn() {
        return ResponseEntity.ok(service.listOwn());
    }

    @PutMapping("/{id}")
    public ResponseEntity<PushSubscriptionVO> update(
            @PathVariable Long id, @Valid @RequestBody PushSubscriptionUpdateDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
