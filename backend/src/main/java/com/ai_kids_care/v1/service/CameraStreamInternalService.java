package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.config.CameraStreamCryptoConfig;
import com.ai_kids_care.v1.entity.CameraStream;
import com.ai_kids_care.v1.internal.ActiveStreamProjection;
import com.ai_kids_care.v1.internal.ActiveStreamVO;
import com.ai_kids_care.v1.internal.StreamClaimRequest;
import com.ai_kids_care.v1.internal.StreamClaimResponse;
import com.ai_kids_care.v1.internal.StreamCredentialDTO;
import com.ai_kids_care.v1.repository.AiModelRepository;
import com.ai_kids_care.v1.repository.CameraStreamRepository;
import com.ai_kids_care.v1.security.AesGcmCryptoUtil;
import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI-internal camera stream operations: credential decrypt, active-stream listing, and the
 * Claim/Lease work-distribution endpoint (shard-live-detection-deployments D2).
 *
 * <p><b>ARC-01 (harden-claim-lease-internal C2)</b>: these three methods previously lived on
 * {@link CameraStreamService} alongside its tenant-scoped {@code @PreAuthorize} CRUD methods. That
 * co-location was a footgun — a future contributor extending the tenant CRUD class by copy/paste
 * could easily land a new unauthenticated/untenanted method there by accident. Splitting them into
 * this dedicated internal service (mirroring the {@link DetectionIngestService} precedent) makes
 * the "no session {@code @PreAuthorize}, no kindergarten filter" posture self-evident from the
 * class itself, not just from a comment. This split changes nothing observable: the HTTP-layer
 * authorization and the wire contract (request/response shapes, Bearer auth) are unchanged.
 *
 * <p><b>Authorization posture (unchanged by the split)</b>: authorization is enforced entirely at
 * the HTTP layer ({@code AiServiceTokenAuthenticationFilter} + {@code hasRole("AI_SERVICE")} on
 * {@code /api/v1/internal/**}). AI calls carry no session/tenant context, so these methods do not
 * add a session-level {@code @PreAuthorize} and do not filter by {@code kindergartenId}
 * (OQ-3=B: the AI service is trusted platform-wide infrastructure that processes every
 * kindergarten's streams; {@code kindergartenId} in the responses is a projected attribution field
 * derived server-side from the camera→kindergarten relation, not an access filter).
 */
@Service
@RequiredArgsConstructor
public class CameraStreamInternalService {

    private final CameraStreamRepository repository;
    private final CameraStreamCryptoConfig cryptoConfig;
    private final AiModelRepository aiModelRepository;
    private final StreamLeaseService leaseService;

    // ADR-0026 Phase 2：内部凭据读路径（D2）。供经 Bearer token 认证的 AI 服务解密读取。
    // 鉴权在 HTTP 层强制（AiServiceTokenAuthenticationFilter + hasRole("AI_SERVICE")）；
    // AI 调用无 session/tenant 上下文，故此方法不叠加会话级 @PreAuthorize，也不做 kindergarten 隔离
    // （OQ-3=B：信任 AI 仅按 stream_id 查自己处理的流；AI 为平台级基建，处理全部园所的流）。
    //
    // shard-live-detection-deployments D2 defense-in-depth：因 AI_SERVICE_TOKEN 为多栈共享 Bearer，
    // 额外校验调用方（X-Deployment-Id 头）当前持有该流的 Redis 租约；不持有/租约不存在 → 404
    // （隐藏存在性，同多租户 404 约定），不透露凭据。正常路径 = worker 刚 claim 完该流即取凭据。
    @Transactional(readOnly = true)
    public StreamCredentialDTO getStreamCredential(Long id, String deploymentId) {
        if (!leaseService.isOwnedBy(id, deploymentId)) {
            throw new EntityNotFoundException("CameraStream not found");
        }
        CameraStream entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("CameraStream not found"));

        String plainPassword = null;
        if (entity.getStreamPasswordCiphertext() != null
                && !entity.getStreamPasswordCiphertext().isBlank()) {
            plainPassword = AesGcmCryptoUtil.decrypt(
                    entity.getStreamPasswordCiphertext(),
                    entity.getStreamPasswordIv(),
                    cryptoConfig.keyForVersion(entity.getStreamPasswordKeyVersion()));
        }

        return new StreamCredentialDTO(
                entity.getId(),
                entity.getSourceUrl(),
                entity.getStreamUser(),
                plainPassword);
    }

    // 方案 A：内部「活跃流清单」读路径。供 AI 多摄像头 supervisor 枚举本部署应消费的活跃流。
    // 鉴权同样在 HTTP 层强制（hasRole("AI_SERVICE")）；AI 调用无 session/tenant 上下文，故不叠加会话级
    // @PreAuthorize（与 getStreamCredential 一致：AI 为平台级基建，处理全部园所的流）。租户归属
    // (kindergartenId) 由 repository 在 JPQL 内从关系投影得出（非加载后过滤），响应不含任何凭据。
    @Transactional(readOnly = true)
    public List<ActiveStreamVO> listActiveStreamsForAi() {
        Long activeModelId = resolveActiveModelId();
        return repository.findActiveStreamsForAi().stream()
                .map(p -> new ActiveStreamVO(p.streamId(), activeModelId, p.kindergartenId()))
                .toList();
    }

    // shard-live-detection-deployments D2：Claim/Lease 动态租约池。鉴权/授权立场同上（内部平台级基建端点，
    // HTTP 层 hasRole("AI_SERVICE") 已强制，不叠加会话级 @PreAuthorize，不做 kindergarten 过滤——AI 处理
    // 全部园所的流，kindergartenId 只是从关系投影出的归属展示字段）。
    //
    // 算法（design D2）：
    //   1) 续租：running 中仍活跃（enabled=true）且租约属主==deploymentId 的流，compare-and-renew 刷新 TTL；
    //      不再活跃 / 租约已属他栈的，不续租（不进 assigned，调用方据此停掉本地 worker）。
    //      续租受 capacity 上限约束（capacity=0 即排空），保证 assigned ≤ capacity。
    //   2) 认领补位：spare = capacity - 已续租数；从活跃流全集里（跳过刚续租的）逐个尝试原子 SET NX 认领，
    //      至多 spare 个——已被他栈持有有效租约的流会因 SET NX 失败被自然跳过，无需显式先查 owner。
    //   3) 返回 assigned = 续租 ∪新认领，逐个组装为与 GET /internal/streams 同型的 ActiveStreamVO。
    @Transactional(readOnly = true)
    public StreamClaimResponse claimStreams(StreamClaimRequest request) {
        Long activeModelId = resolveActiveModelId();
        List<ActiveStreamProjection> activeStreams = repository.findActiveStreamsForAi();

        List<ActiveStreamVO> assigned = new ArrayList<>();
        Set<Long> renewedIds = new HashSet<>();

        // 1) 续租：仅对仍活跃的 running 流尝试；不活跃的直接跳过（不进 assigned）。
        //    续租同样受 capacity 约束：capacity 下调（含降为 0=排空）时，超出上限的旧租约不再续租、
        //    随 TTL 自然过期，调用方据 assigned 停掉对应 worker。保证「assigned ≤ capacity」恒成立。
        for (ActiveStreamProjection projection : activeStreams) {
            if (renewedIds.size() >= request.capacity()) {
                break;
            }
            if (!request.runningOrEmpty().contains(projection.streamId())) {
                continue;
            }
            if (leaseService.renew(projection.streamId(), request.deploymentId())) {
                renewedIds.add(projection.streamId());
                assigned.add(new ActiveStreamVO(projection.streamId(), activeModelId, projection.kindergartenId()));
            }
        }

        // 2) 认领补位：按活跃流全集顺序（JPQL order by id，确定性），跳过刚续租的，至多认领 spare 个。
        int spare = request.capacity() - renewedIds.size();
        for (ActiveStreamProjection projection : activeStreams) {
            if (spare <= 0) {
                break;
            }
            if (renewedIds.contains(projection.streamId())) {
                continue;
            }
            if (leaseService.tryClaim(projection.streamId(), request.deploymentId())) {
                assigned.add(new ActiveStreamVO(projection.streamId(), activeModelId, projection.kindergartenId()));
                spare--;
            }
        }

        return new StreamClaimResponse(assigned);
    }

    // Open Question 4：当前无 per-stream 模型映射；V1 取平台活跃模型的最低 id 作为每路流的 modelId。
    // 无活跃模型时返回 null，由 supervisor/worker 端的 MODEL_ID env 兜底。
    private Long resolveActiveModelId() {
        return aiModelRepository.findModelIdsByStatusOrderById(StatusEnum.ACTIVE)
                .stream().findFirst().orElse(null);
    }
}
