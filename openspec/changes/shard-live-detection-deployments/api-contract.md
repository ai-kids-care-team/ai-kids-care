# API 契约 — shard-live-detection-deployments（冻结）

> 本 change **无前端面**。契约双侧 = **backend（Java，`backend/`）** 与 **AI supervisor/worker（Python，`ai/`）**。
> backend-implementer 与 AI-implementer 各对着这份冻结契约**全并行**实现；fan-in 时 lead + integration-analyst 逐字段比对。
> 路径以实际 controller 为准；internal 前缀 `/api/v1/internal/**` = Bearer `AI_SERVICE_TOKEN`（`ROLE_AI_SERVICE`）、CSRF 豁免。
> 多租户不出现在契约参数里——AI 为平台级基建，`kindergartenId` 由后端从 `streamId` 派生返回（供展示/归属），**非入参、非过滤键**。

---

## 端点 1（新增）：`POST /api/v1/internal/streams/claim` — 认领/续租工作池中的流

- **路径**：`/api/v1/internal/streams/claim`（挂在既有 `StreamCredentialController`，`@RequestMapping("/api/v1/internal/streams")`，新增 `@PostMapping("/claim")`）
- **方法**：POST
- **鉴权**：internal → Bearer `AI_SERVICE_TOKEN`（`ROLE_AI_SERVICE`），CSRF 豁免（HTTP 层 `hasRole("AI_SERVICE")`，与既有 internal 端点同）
- **授权**：AI 平台级基建，service 方法**不叠加**会话级 `@PreAuthorize`（无 session/tenant 上下文，与 `listActiveStreamsForAi`/`getStreamCredential` 一致）

### 请求
- **DTO 类名**：`StreamClaimRequest`（camelCase，与既有 `DetectionEventIngestRequest` 风格一致）

| 字段 | 类型 | 可空 | 校验/说明 |
|------|------|------|-----------|
| `deploymentId` | String | 否 | `@NotBlank`。AI 部署（GPU 栈）唯一标识，用于租约归属；每栈唯一（由 runbook/`DEPLOYMENT_ID` env 保证） |
| `capacity` | int | 否 | `@Min(0)`。该栈可并行承载的 worker 上限（= AI 侧 `MAX_WORKERS`） |
| `running` | List<Long> | 否（可空数组） | 该栈当前正在跑的 `streamId` 列表；用于续租。空列表合法（冷启动） |

### 响应
- **VO 类名**：`StreamClaimResponse`，内含 `assigned: List<ActiveStreamVO>`
- **`ActiveStreamVO`**（复用既有，`GET /internal/streams` 已返回同型；如字段不足由 backend-implementer 补齐并保持两端点同型）：

| 字段 | 类型 | 可空 | 说明 |
|------|------|------|------|
| `streamId` | Long | 否 | `camera_streams.id` |
| `modelId` | Long | 否 | V1 = 当前唯一活跃 assault 模型（后端由活跃 `ai_models` 解析）；多模型映射 later |
| `kindergartenId` | Long | 否 | 从 `cctvCameras.kindergarten.id` 在 JPQL 内投影派生（供归属展示，非过滤入参） |

- **`assigned` 语义** = 该栈本轮应运行的**完整**流集（续租的 + 新认领的）。AI 侧据此**全量 reconcile**（不在 assigned 内的本地 worker 停掉）。

### 后端认领算法（service 层，须实现）
1. 取活跃流全集（`enabled = true`，谓词进 JPQL）。
2. **续租**：对 `running` 中「仍活跃 且 当前租约属主 == `deploymentId`」的流，compare-and-renew 刷新 TTL（Lua：`if GET==depId then PEXPIRE`，防误抢他栈租约）。
3. **认领补位**：`spare = capacity - 已续租数`；从「活跃 且 无有效租约（未认领/已过期）」的流中，原子 `SET stream_lease:{streamId} deploymentId NX EX <ttl>` 认领**至多** `spare` 个。
4. 返回 `assigned` = 续租集 ∪ 新认领集，逐个组装为 `ActiveStreamVO`。

### 租约存储（Redis，无 schema）
- key：`stream_lease:{streamId}`，value：`deploymentId`，TTL：`<ttl>`（配置项，默认 **60s**）。
- 复用既有 `RedisConnectionFactory`（参考 `security/LoginThrottleService` 的 Redis 用法）。
- 原子认领 `SET NX EX`；续租 Lua compare-and-renew。
- **配置**：`ai.stream-lease.ttl-seconds`（默认 60）。约束：TTL 须 ≥ ~3× AI poll 周期（poll 默认 20s → TTL 60s）。

### 错误契约
- 缺/非法 body（`deploymentId` 空、`capacity` < 0）→ 400（既有全局异常处理）。
- 鉴权失败（无/错 Bearer、非 `ROLE_AI_SERVICE`）→ 401/403（HTTP 层，与既有 internal 端点一致）。
- 并发同流认领 → `SET NX` 保证至多一栈获租；未获者该流不出现在其 `assigned`。

### AI 对齐点（`ai/`）
- `ai_app/supervisor.py`：`StreamSupervisor` 的「desired 流来源」从既有 `GET /internal/streams` 全列表**改为**周期性 `POST /streams/claim`（带 `deploymentId=DEPLOYMENT_ID`、`capacity=MAX_WORKERS`、`running=当前 worker 的 streamId 集`）；claim 兼作心跳续租。
- 据响应 `assigned` 全量 reconcile 有界进程池（已有 `reconcile()`/`_desired()`/MAX_WORKERS WARN 复用）。
- `DEPLOYMENT_ID` 经 env 注入，缺失 fail-fast（与 `AI_SERVICE_TOKEN` 同规）。
- poll 周期 env `STREAM_POLL_INTERVAL_SEC` 默认 **20**。

---

## 端点 2（修改）：`GET /api/v1/internal/streams/{id}/credentials` — 加租约归属校验

- **变更**：在既有取凭据端点上增加「调用方须当前持有该流租约」校验（defense-in-depth）。
- **调用方身份**：因 `AI_SERVICE_TOKEN` 共享，凭据请求须携带 **`X-Deployment-Id` 请求头**标识调用栈。
- **校验**：若 `stream_lease:{id}` 不存在或其属主 ≠ `X-Deployment-Id` → **404**（隐藏存在性，与多租户 404 约定一致），不返回凭据。
- **正常路径**：worker 在 claim 拿到 `assigned` 后立即取凭据，此时租约存在且属主为本栈 → 200 返回既有 `StreamCredentialDTO`（**形状不变**）。
- **AI 对齐点**：`ai/src/ai_app/utils/stream_credentials.py` 取凭据时附带 `X-Deployment-Id: {DEPLOYMENT_ID}` 头。

### `StreamCredentialDTO`（形状不变，仅列出供双侧确认）
| 字段 | 类型 | 可空 | 说明 |
|------|------|------|------|
| （既有字段保持不变） | — | — | 解密后 URL/凭据，供拼接流地址；本 change 不改其 shape |

---

## 部署契约（`ai/`，gated 已批准）
- 新增 `ai/docker-compose.gpu.yml`：**唯一**承载 nvidia 设备预留（`deploy.resources.reservations.devices`），覆盖 `ai-inference` + `ai-live-supervisor`。
- `ai/docker-compose.yml`：`ai-live-supervisor` 加 `profiles: [live]` + `DEPLOYMENT_ID` env；base **无 GPU 硬要求**。
- 无 GPU 机：`docker compose -f ai/docker-compose.yml config -q` 通过、默认 `up` 只起 `ai-inference`。
- GPU 机：`docker compose -f ai/docker-compose.yml -f ai/docker-compose.gpu.yml --profile live up`。

---

## 冻结确认清单
- [x] 端点/路径/方法/鉴权 明确
- [x] 请求 DTO 字段级（类型/可空/校验）明确
- [x] 响应 VO 字段级（复用 `ActiveStreamVO`，两端点同型）明确
- [x] 租约存储/TTL/原子性/配置项 明确
- [x] 错误契约（400/401/403/404）明确
- [x] 双侧对齐点（backend service+Redis / AI supervisor+credentials+env）明确
- [x] 无 schema 迁移（租约用 Redis）
