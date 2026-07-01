package com.ai_kids_care.v1.internal;

import com.ai_kids_care.v1.service.CameraStreamService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内部流接口 — AI 服务专用（{@code ROLE_AI_SERVICE} Bearer token）。
 *
 * <ul>
 *   <li>{@code GET /api/v1/internal/streams}（方案 A）：返回本部署应消费的活跃流
 *       {@code [{streamId, modelId, kindergartenId}]}，供 AI 多摄像头 supervisor 枚举并 reconcile。
 *       <b>不含任何解密凭据。</b></li>
 *   <li>{@code POST /api/v1/internal/streams/claim}（shard-live-detection-deployments D2）：
 *       Claim/Lease 动态租约池——多 GPU 栈按容量续租/认领流，供跨栈均衡分发。</li>
 *   <li>{@code GET /api/v1/internal/streams/{id}/credentials}（ADR-0026 Phase 2 D2 读路径，
 *       shard-live-detection-deployments D2 加租约归属校验）：返回解密后的 {@link StreamCredentialDTO}，
 *       供 AI 推理服务拼接 RTSP URL；调用方须经 {@code X-Deployment-Id} 头证明当前持有该流租约。</li>
 * </ul>
 *
 * <p>鉴权由 {@code AiServiceTokenAuthenticationFilter} + {@code SecurityConfig} 的
 * {@code hasRole("AI_SERVICE")} 规则在 HTTP 层强制（OQ-1=A 共享 Bearer token）；故 service 方法不再叠加
 * 会话级 {@code @PreAuthorize}（AI 调用无 session/tenant 上下文）。
 *
 * <p>{@code @Hidden}：不进 OpenAPI / Swagger（内部链路不对外暴露 schema 形状）。
 */
@Hidden
@RestController
@RequestMapping("/api/v1/internal/streams")
@RequiredArgsConstructor
public class StreamCredentialController {

    private final CameraStreamService service;

    @GetMapping
    public ResponseEntity<List<ActiveStreamVO>> listActiveStreams() {
        return ResponseEntity.ok(service.listActiveStreamsForAi());
    }

    @PostMapping("/claim")
    public ResponseEntity<StreamClaimResponse> claimStreams(@Valid @RequestBody StreamClaimRequest request) {
        return ResponseEntity.ok(service.claimStreams(request));
    }

    @GetMapping("/{id}/credentials")
    public ResponseEntity<StreamCredentialDTO> getStreamCredentials(
            @PathVariable Long id,
            @RequestHeader(value = "X-Deployment-Id", required = false) String deploymentId) {
        return ResponseEntity.ok(service.getStreamCredential(id, deploymentId));
    }
}
