# Tasks — shard-live-detection-deployments

> 依赖：本 change 排在 `ai-ingest-detection-events-v1` 之后（精化其 supervisor / 列流端点）。
> 破坏性/gated 项（部署面变更）apply 前须维护者**逐项批准**。**无 schema 迁移**（租约用 Redis）。
> 状态（2026-07-01）：代码实现 + 门禁清零完成；gated 实测项（4.3 / 7.2）+ 归档面文档（6.2/6.3 跨网 runbook）留维护者/归档时。

## 1. 维护者决策与批准（前置）

- [x] 1.1 批准部署面变更（新增 `ai/docker-compose.gpu.yml` + supervisor `profiles` + `DEPLOYMENT_ID`）——维护者 2026-07-01 已批准
- [x] 1.2 租约 TTL / poll 周期初值已定：**poll 20s / TTL 60s**（design OQ-1，维护者采纳默认值）
- [x] 1.3 分发模型已定：Claim/Lease 动态租约池（按容量均衡，非按园所），Redis 存租约无 schema
- [x] 1.4 单栈执行模型已定：方案 A 有界进程池 + MAX_WORKERS 实测；方案 B 列独立 follow-up

## 2. 后端：Claim/Lease 工作分发

- [x] 2.1 `POST /api/v1/internal/streams/claim`（`ROLE_AI_SERVICE`）：body `{deploymentId, capacity, running[]}` → `{assigned:[{streamId, modelId, kindergartenId}]}`
- [x] 2.2 续租：对 `running` 中仍 `enabled=true` 且租约属主==deploymentId 的流，Lua compare-and-renew 刷新 TTL（**续租受 capacity 上限约束**，含 capacity=0 排空）
- [x] 2.3 认领补位：`spare = capacity - 已续租`；从活跃且无有效租约的流原子 `SET NX EX` 认领至多 spare 个
- [x] 2.4 Redis 租约存取：`StreamLeaseService`，key `stream_lease:{streamId}=deploymentId`，TTL 可配；复用既有 `StringRedisTemplate`
- [x] 2.5 `CameraStreamRepository#findActiveStreamsForAi` 活跃流全集（`enabled=true` 谓词进 JPQL）；`modelId` 由 service `resolveActiveModelId` 解析随流返回
- [x] 2.6 凭据端点租约校验：`GET /internal/streams/{id}/credentials` + `X-Deployment-Id` 头，`isOwnedBy` 不持有→404（隐藏存在性）；`deploymentId` 加 `@Size(max=128)` 上限（SEC-L1）
- [x] 2.7 后端契约测试 `InternalStreamClaimApiTest`(17) + `InternalStreamClaimLeaseExpiryApiTest`(1)：容量上限/续租/过期再分配/跨租约凭据 404/4xx/并发 SET NX 不双占/capacity 上限含 0 排空
- [x] 2.8 `./gradlew test` 全套件绿：**330 tests / 0 fail / 0 errors**（develop，零回归）

## 3. AI：supervisor 改由 claim 驱动 + 有界 worker 池

- [x] 3.1 `ai_app/supervisor`：周期性 claim（`DEPLOYMENT_ID`/`capacity=MAX_WORKERS`/`running`），`set_stream_source()`+`make_claim_based_lister()` 适配（既有 8 supervisor 测试零破坏），claim 兼心跳续租
- [x] 3.2 据 `assigned` 全量 reconcile 有界进程池（复用 `reconcile()`/`_desired()`/MAX_WORKERS WARN）
- [x] 3.3 不静默丢弃：claim 失败保留现有 worker + log + 下轮重试，绝不 crash
- [x] 3.4 `DEPLOYMENT_ID` env + fail-fast（缺失 RuntimeError，与 `AI_SERVICE_TOKEN` 同规）；凭据取用附 `X-Deployment-Id`
- [x] 3.5 AI 单测（mock claim，无 GPU/真流）：`test_stream_claim.py` + `test_stream_supervisor_claim.py`(12) + `test_stream_credentials.py` 扩展
- [x] 3.6 `PYTHONPATH=src python -m pytest tests/ -v`：**134 passed**

## 4. AI：MAX_WORKERS 基准实验

- [x] 4.1 基准脚本 `ai/scripts/benchmark_max_workers.py`（`nvidia-smi` 采样 CLI，无 ML 依赖）
- [x] 4.2 runbook `ai/docs/max-workers-benchmark-runbook.md`（判定：利用率高 + 显存留 ~15% headroom + 延迟不击穿实时性 → 最大 N；含空结果表）
- [ ] 4.3 端到端实测取值 —— **留维护者在 GPU 机执行**（本机无 GPU/真流）

## 5. 部署：GPU overlay + profile（gated，已批准）

- [x] 5.1 `ai/docker-compose.gpu.yml`：唯一承载 nvidia 设备预留，覆盖 `ai-inference` + `ai-live-supervisor`
- [x] 5.2 `ai/docker-compose.yml` `ai-live-supervisor` 加 `profiles: ["live"]` + `DEPLOYMENT_ID` env；base 无 GPU 依赖；`MAX_WORKERS` 默认 2、`STREAM_POLL_INTERVAL_SEC` 默认 20
- [x] 5.3 `config -q` 两路径均过：无 GPU base（默认只起 ai-inference）+ GPU overlay（`--profile live`）
- [x] 5.4 确认无 GPU 机 base `config`/默认 `up` 不被 GPU 卡死；根 compose 仍不含 AI 栈

## 6. 文档 / spec 收口

- [x] 6.1 `ai-detection` spec 增量：Claim/Lease 分发（含 capacity 上限/排空 scenario）+ worker 池有界容量/超额信号 + GPU overlay 不破坏无 GPU + V1 assault-only（ADDED，层叠 ai-ingest）
- [ ] 6.2 `CLAUDE.md` AI 段补「Claim/Lease + GPU overlay」形态 —— **留归档时**（当前实现于 develop 但 change 未归档、未部署，避免 CLAUDE.md 描述非 canonical 现状）
- [~] 6.3 部署 runbook：MAX_WORKERS runbook 已建（4.2）；**跨网 mesh VPN + GPU 主机准备（NVIDIA Container Toolkit + cu130 驱动兼容）+ DEPLOYMENT_ID 分配**留归档/部署时补
- [x] 6.4 关闭 ai-ingest design OQ-2（租户/流范围，被池模型消解）、OQ-5（assault-only）；引用本 change

## 7. 验证与收尾

- [x] 7.1 `openspec validate shard-live-detection-deployments --strict` 通过
- [ ] 7.2 端到端集成验证 —— **留维护者在 GPU + 真流 + 多栈环境**：两栈按容量均分流、宕机租约过期被接管、加机自动摊入、瞬时双跑经后端幂等去重无重复 event
- [x] 7.3 Non-goal 边界复核：方案 B（共享批推理池）未混入、接口边界为其预留；记入 follow-up（design OQ-4）
