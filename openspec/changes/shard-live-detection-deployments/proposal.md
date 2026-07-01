## Why

`ai-ingest-detection-events-v1` 让 ADR-0015 V1 闭环的代码两侧就绪，并引入了「单 AI 部署 = 一个 supervisor 枚举活跃流 + 每流一 worker」的运维形态。但把它真正投产时，维护者（2026-07-01）确认了一个该形态无法满足的现实约束：

> **单张 GPU 无法承载全部租户的检测。** 一个租户有多路 CCTV，多租户叠加后一个 GPU 上跑不下所有流——每路 worker 各自 `from_pretrained` 加载**一份** VideoMAE 副本（`stream_live_alert_service.py:208-211`），每进程还各付一份 CUDA context 开销。生产形态必然是**部署多个 AI 栈、每栈一张 GPU**。

如何把流分配到这些 GPU 栈——维护者进一步明确：**不按园所（或园所组）分片**。因为各园 CCTV 数量悬殊（A 园 20 路、B 园 3 路），按园所绑死 GPU 会造成一台过载、一台闲置。**要把所有活跃流当成一个工作池，按各 GPU 栈的容量均摊、动态均衡负载、自动容错。**

这个「按池均衡」形态暴露当前设计的缺口：

1. **流分发没有工作池/均衡机制。** `CameraStreamRepository#findActiveStreamsForAi()` 无条件返回**全库** `enabled` 流；`GET /api/v1/internal/streams` 因此对所有 AI 栈返回同一份全集。多个 GPU 栈会各自枚举到**全部**流并都尝试运行 → 同一路流被多栈重复消费、无任何负载分配。需要一个后端协调的**认领/租约（claim/lease）**工作分发：每个 GPU 栈按自己的容量从未认领的流池里认领，宕机自动释放、加机自动分摊。
2. **worker 数量没有受管的容量上界语义。** `MAX_WORKERS` 环境变量已存在，但「超过上界怎么办」没有明确、可观测行为；单 GPU 能并行承载几路 VideoMAE（受**显存**限制）只能实测得出，且须是「高负载但不 OOM」的经验值。
3. **GPU 直通与「无 GPU 机器仍须能 `compose up`」冲突。** worker 用上 GPU 需给容器加 nvidia 设备预留；若写进 base compose，当前这台无 GPU 开发机（及 CI `Compose config`）就 `up` 不起来。维护者明确：**批准部署面变更，但不得让无 GPU 机器无法 `compose up`。**

本提案把这三点收口，使「按池均衡、多 GPU、多 AI 栈」的真流检测**可正确部署、动态均衡、且不破坏无 GPU 环境**。

**执行模型决策（维护者 2026-07-01）**：单栈内 worker 池采用**方案 A——有界进程池（每流一进程）+ 经验实测的 `MAX_WORKERS` 上界**；跨栈流分发采用 **Claim/Lease 动态租约池**（Redis TTL 租约，无 schema 迁移）。**不**在本 change 做「解码器 + 共享批推理池」的密度优化（方案 B）——见 Non-goals。

**多租户隔离不受影响**：AI 栈是基础设施、非租户，本来就以 `ROLE_AI_SERVICE` 可见全部流；一台 GPU 跑多个园所的流完全合规——**租户归属仍由后端在 ingest 时从 `streamId` 推导**，与哪台 GPU 在跑无关。

**依赖**：本 change 是 `ai-ingest-detection-events-v1` 的 follow-up，**排在其后**（精化其 supervisor / 列流端点行为）。

## What Changes

- **后端：Claim/Lease 工作分发（核心）。** 新增内部端点 `POST /api/v1/internal/streams/claim`（`ROLE_AI_SERVICE`）：调用方报「`deploymentId` + 容量 `capacity` + 当前在跑 `running[]`」，后端①续租其仍在跑且仍活跃的流，②若有空槽从**未被认领的活跃流**里认领补到容量，③返回该栈应跑的最新流集 `[{streamId, modelId, kindergartenId}]`（不含凭据）。租约存 **Redis**（`stream_lease:{streamId}=deploymentId`，TTL = 数倍 poll 周期），原子认领用 `SET NX`、仅属主可用 compare-and-renew 续租。**无 schema 迁移。**
- **后端：租约到期自动再均衡 / failover。** 某栈停止续租（宕机/网络断）→ 其租约 TTL 到期 → 相关流回到未认领池 → 其它有空栈下轮认领。新增摄像头进池被有空栈认领；下线摄像头（`enabled=false`）不再续租、被 supervisor 停掉。
- **后端：凭据端点校验租约（defense-in-depth）。** `GET /api/v1/internal/streams/{id}/credentials` 校验调用方当前**持有该流租约**；否则 404（隐藏存在性，与多租户 404 约定一致）。
- **AI：supervisor 改由 claim 端点驱动 + worker 池有界容量。** supervisor 周期性 claim（带 `DEPLOYMENT_ID`、`MAX_WORKERS` 作为容量、当前在跑流集），据返回流集 reconcile 有界、自愈进程池；claim 兼作心跳续租。超上界（后端已按容量分配，正常不会超）或返回流集异常时不静默丢弃——显式 `log`。
- **AI：`MAX_WORKERS` 基准实验流程。** 目标 GPU 主机上 N 递增 + `nvidia-smi` 观测显存/利用率/延迟，测「高负载不 OOM」最大并发数作为该机型 `MAX_WORKERS`（不同 GPU 不同）。
- **部署：GPU 直通只放 opt-in overlay + compose profile。** 新增 `ai/docker-compose.gpu.yml` 承载 nvidia 设备预留；base `ai/docker-compose.yml` 保持无 GPU 依赖（CPU 可回退、CI `Compose config` 照常）。`ai-live-supervisor` 归入 compose `profile`，无 GPU 机默认 `up` 只起 `ai-inference`。GPU 主机用 `-f base -f gpu.yml --profile <live>`。**BREAKING（部署面）**，apply 前须维护者批准。
- **收口：V1 检测目标范围 = assault。** 明确 V1 只检测 `assault`（状态机现状），其余类型 later version；关闭 ai-ingest design OQ-5。
- **拓扑文档**：记录「AI 栈随 GPU 异地部署、经 mesh VPN 连后端」形态（`JAVA_BACKEND_URL` 经加密通道，避免 internal 端点公网暴露 + token 明文跨网）。

**Non-goals（明确排除）**：
- **不做**「每流解码器 + 共享批推理池」的密度优化（方案 B / 集中推理 + 批处理）。复杂度数量级跳升，应由方案 A 上线后的**真实密度数字**驱动；**列为独立 follow-up change**，接口边界画好以便替换执行后端而不动本 change 的 claim/租约与 ingest 契约。
- **不做** token→租户绑定分片（被 Claim/Lease 池取代，维护者 2026-07-01 明确按池均衡而非按园所）。
- 不重做 ai-ingest 已实现的 ingest client / backend 端点 / evidence / 重试 / severity / SSE / 读 API / 复核 / supervisor 基础形态。
- 不做多类 `target_label`（V1 只 assault）、对象存储 evidence、SSE 多实例 fanout、GPU 推理加速、模型/算法改动。
- 不引入跨进程持久化失败队列（沿用 best-effort、绝不 crash）。

## Capabilities

### Modified Capabilities
- `ai-detection`：在 ai-ingest 引入的「supervisor + 列流端点」之上，新增①**Claim/Lease 工作分发**（跨 GPU 栈按容量均衡 + 租约 failover）、②worker 池**有界容量 + 超额显式信号**、③**GPU 部署 overlay 不破坏无 GPU 环境**、④**V1 检测目标 = assault** 的范围收口。均以 ADDED requirement 层叠（不改 ai-ingest 未归档 delta 的既有条目）。

## Impact

- **Backend**：新增 `POST /internal/streams/claim`（`StreamCredentialController` 或新 controller）；claim 服务逻辑（续租/认领/返回）；Redis 租约存取（复用既有 `RedisConnectionFactory`，今仅 Session + 限流，新增租约用途，无业务缓存扩张）；`CameraStreamRepository` 增「未认领活跃流」查询辅助；凭据端点加租约校验。**无 Flyway 迁移。**
- **AI**：`ai_app/supervisor` 改由 claim 驱动 + `DEPLOYMENT_ID` + 心跳续租 + 有界池；新增 `MAX_WORKERS` 基准脚本/文档；不改 worker 单流逻辑。
- **部署**：新增 `ai/docker-compose.gpu.yml`；`ai-live-supervisor` 加 `profiles` + `DEPLOYMENT_ID`；生产/CD 叠加层按 GPU 主机形态注入。
- **文档**：`ai-detection` spec 收口；`CLAUDE.md` AI 段补「Claim/Lease 池 + GPU overlay」形态；新增部署 runbook（跨网 VPN + GPU 主机准备）。
- **回滚**：claim 端点 / Redis 租约 / worker 池 / overlay 均新增，回滚即移除；无 schema、无破坏性数据变更。
