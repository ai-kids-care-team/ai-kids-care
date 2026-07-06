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
import com.ai_kids_care.v1.security.audit.AuditAction;
import com.ai_kids_care.v1.security.audit.AuditEvent;
import com.ai_kids_care.v1.security.audit.AuditResult;
import com.ai_kids_care.v1.security.audit.SecurityAuditWriter;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
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
 *
 * <p><b>SEC-05 defense-in-depth (harden-claim-lease-internal C2)</b>: because
 * {@code AI_SERVICE_TOKEN} is a single Bearer token shared by every GPU stack (accepted
 * blast-radius risk, OQ-3=B), a successful credential read and every newly-won stream claim are
 * recorded via {@link SecurityAuditWriter} as {@code S1_EVIDENCE_READ} — forensic visibility if
 * that shared token is ever abused. The audit payload never carries the plaintext credential or
 * the token itself (invariant #5): it is scoped {@code PLATFORM} (no session/tenant actor exists
 * to attribute the event to), records the caller's {@code deploymentId} in {@code effectiveRole}
 * (the closest existing "who acted" column for a non-session actor), and the stream id as
 * {@code resourceId}. Renewals of an already-held lease are not re-audited on every poll — only a
 * newly-claimed stream (a lease this deployment did not already hold) triggers a new record, so
 * steady-state polling (the common case) writes nothing.
 *
 * <p><b>PRF-09 / PRF-11 (harden-claim-lease-internal C2)</b>: {@link #claimStreams} reads the active
 * stream projection via one short, Spring-Data-JPA-managed transaction (the repository call opens
 * and closes its own transaction because this method itself is not {@code @Transactional}), then
 * performs its Redis renew/claim work with no open transaction / Hikari connection held across the
 * external IO. That Redis work is batched: {@link StreamLeaseService#renewBatch} and
 * {@link StreamLeaseService#tryClaimBatch} each execute as a single Lua script covering every
 * candidate, instead of one Redis round trip per stream — collapsing the previous
 * O(capacity + activeStreamCount) round trips down to O(1) per {@code claimStreams} call, while
 * keeping the exact same per-key atomicity (each candidate is still an independent
 * compare-and-renew / {@code SET NX}). {@link #getStreamCredential} drops the same blanket
 * {@code @Transactional} for the same reason (its Redis {@code isOwnedBy} check and the audit
 * write are each independent of any DB transaction; the entity fetch gets its own short
 * transaction from Spring Data).
 */
@Service
@RequiredArgsConstructor
public class CameraStreamInternalService {

    private static final String RESOURCE_TYPE_CREDENTIAL = "CAMERA_STREAM_CREDENTIAL";
    private static final String RESOURCE_TYPE_CLAIM = "CAMERA_STREAM_CLAIM";

    private final CameraStreamRepository repository;
    private final CameraStreamCryptoConfig cryptoConfig;
    private final AiModelRepository aiModelRepository;
    private final StreamLeaseService leaseService;
    private final SecurityAuditWriter auditWriter;

    // ADR-0026 Phase 2：内部凭据读路径（D2）。供经 Bearer token 认证的 AI 服务解密读取。
    // 鉴权在 HTTP 层强制（AiServiceTokenAuthenticationFilter + hasRole("AI_SERVICE")）；
    // AI 调用无 session/tenant 上下文，故此方法不叠加会话级 @PreAuthorize，也不做 kindergarten 隔离
    // （OQ-3=B：信任 AI 仅按 stream_id 查自己处理的流；AI 为平台级基建，处理全部园所的流）。
    //
    // shard-live-detection-deployments D2 defense-in-depth：因 AI_SERVICE_TOKEN 为多栈共享 Bearer，
    // 额外校验调用方（X-Deployment-Id 头）当前持有该流的 Redis 租约；不持有/租约不存在 → 404
    // （隐藏存在性，同多租户 404 约定），不透露凭据。正常路径 = worker 刚 claim 完该流即取凭据。
    //
    // 不再用 @Transactional 包住整个方法（PRF-11 立场同 claimStreams）：leaseService.isOwnedBy 是单次
    // Redis GET，与事务无关；repository.findById 由 Spring Data 自身的短事务管理；随后的 AES-GCM 解密
    // 是纯 CPU 运算；最后的审计写入走 SecurityAuditWriter 自己的 REQUIRES_NEW 事务。全程没有一段代码
    // 需要跨越 DB 事务持有连接。
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

        recordEvidenceRead(id, deploymentId, RESOURCE_TYPE_CREDENTIAL);

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
    // 算法（design D2，harden-claim-lease-internal C2 批量化为 PRF-09/PRF-11）：
    //   1) 续租：running 中仍活跃（enabled=true）且租约属主==deploymentId 的流，批量 compare-and-renew
    //      刷新 TTL（一次 Redis 往返覆盖全部候选，capacity 上限在 Lua 脚本内维持，语义与逐个调用等价）。
    //      不再活跃 / 租约已属他栈的，不续租（不进 assigned，调用方据此停掉本地 worker）。
    //   2) 认领补位：spare = capacity - 已续租数；对活跃流全集里跳过刚续租的候选做批量 SET NX 认领，
    //      至多 spare 个——已被他栈持有有效租约的流仍会因 SET NX 失败被自然跳过。
    //   3) 新认领（非续租）的每个 streamId 各写一条 S1_EVIDENCE_READ 审计（SEC-05）。
    //   4) 返回 assigned = 续租 ∪ 新认领，逐个组装为与 GET /internal/streams 同型的 ActiveStreamVO。
    //
    // PRF-11：本方法不再是 @Transactional —— repository.findActiveStreamsForAi() 由 Spring Data 自身
    // 短事务管理，取回投影后即关闭；随后的批量 Redis 调用与审计写入都在该短事务之外执行，不跨外部 IO
    // 持有 Hikari 连接。
    public StreamClaimResponse claimStreams(StreamClaimRequest request) {
        Long activeModelId = resolveActiveModelId();
        List<ActiveStreamProjection> activeStreams = repository.findActiveStreamsForAi();

        List<ActiveStreamVO> assigned = new ArrayList<>();
        Set<Long> renewedIds = new HashSet<>();

        // 1) 续租（批量）：候选 = 活跃流全集里被调用方报告为 running 的那些，保持 activeStreams 的
        //    id 顺序（与逐条实现的迭代顺序一致）。capacity 上限由批量脚本内部维持。
        List<Long> renewCandidates = activeStreams.stream()
                .map(ActiveStreamProjection::streamId)
                .filter(id -> request.runningOrEmpty().contains(id))
                .toList();
        Set<Long> renewed = leaseService.renewBatch(renewCandidates, request.deploymentId(), request.capacity());
        for (ActiveStreamProjection projection : activeStreams) {
            if (renewed.contains(projection.streamId())) {
                renewedIds.add(projection.streamId());
                assigned.add(new ActiveStreamVO(projection.streamId(), activeModelId, projection.kindergartenId()));
            }
        }

        // 2) 认领补位（批量）：候选 = 活跃流全集里跳过刚续租的，同样保持 id 顺序。
        int spare = request.capacity() - renewedIds.size();
        List<Long> claimCandidates = activeStreams.stream()
                .map(ActiveStreamProjection::streamId)
                .filter(id -> !renewedIds.contains(id))
                .toList();
        Set<Long> claimed = leaseService.tryClaimBatch(claimCandidates, request.deploymentId(), spare);
        for (ActiveStreamProjection projection : activeStreams) {
            if (claimed.contains(projection.streamId())) {
                assigned.add(new ActiveStreamVO(projection.streamId(), activeModelId, projection.kindergartenId()));
            }
        }

        // 3) SEC-05：仅新认领（非续租）的流各写一条审计——稳态轮询（全是续租）不产生任何审计写入。
        for (Long newlyClaimedId : claimed) {
            recordEvidenceRead(newlyClaimedId, request.deploymentId(), RESOURCE_TYPE_CLAIM);
        }

        return new StreamClaimResponse(assigned);
    }

    // Open Question 4：当前无 per-stream 模型映射；V1 取平台活跃模型的最低 id 作为每路流的 modelId。
    // 无活跃模型时返回 null，由 supervisor/worker 端的 MODEL_ID env 兜底。
    private Long resolveActiveModelId() {
        return aiModelRepository.findModelIdsByStatusOrderById(StatusEnum.ACTIVE)
                .stream().findFirst().orElse(null);
    }

    /**
     * SEC-05：写一条 {@code S1_EVIDENCE_READ} 审计（best-effort，独立 {@code REQUIRES_NEW} 事务，见
     * {@link SecurityAuditWriter}——不占用调用方任何已开事务/连接）。绝不含明文凭据/token（invariant #5）：
     * payload 只有 deploymentId（{@code effectiveRole}）、streamId（{@code resourceId}）、resourceType、
     * 固定 {@code SUCCESS} 结果（方法只在凭据已解密成功 / 流已成功新认领时调用）。
     */
    private void recordEvidenceRead(Long streamId, String deploymentId, String resourceType) {
        auditWriter.record(AuditEvent.builder()
                .action(AuditAction.S1_EVIDENCE_READ)
                .result(AuditResult.SUCCESS)
                .scopeType(UserRoleAssignmentScopeType.PLATFORM)
                .effectiveRole(deploymentId)
                .resourceType(resourceType)
                .resourceId(streamId)
                .build());
    }
}
