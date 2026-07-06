## Why

`shard-live-detection-deployments`（D2 Claim/Lease，代码已落 develop、change 28/32 仅剩 GPU/部署任务）落地后，2026-07-06 多角度分析在 claim/lease 内部面命中一簇**非破坏性、可立即收口**的硬化项（同根：`CameraStreamService` 承载了本该独立的 AI-internal 面）：

- **ARC-01（HIGH，footgun）**：`CameraStreamService` 把 4 个 tenant `@PreAuthorize` CRUD 方法与 3 个**无会话级 `@PreAuthorize`、无租户过滤**的 AI-internal 方法（`getStreamCredential`/`listActiveStreamsForAi`/`claimStreams`）混在同一类，破坏了本仓 `DetectionIngestService` 独立拆分先例。非活越权洞，但「未来贡献者复制粘贴误发无鉴权端点」的结构隐患。
- **SEC-05（MEDIUM）**：凭据解密（`getStreamCredential`）与 `claimStreams` **无审计**——`AuditAction.S1_EVIDENCE_READ` 已定义但**零调用点**。这正是维护者为「接受 SEC-06 共享 token 跨租户 blast radius（OQ-3=B，靠 VPN）」所选的**纵深防御支点**：补上审计，SEC-06 的接受才成立（滥用可取证）。
- **SEC-07（LOW）**：`StreamClaimRequest.capacity` 有 `@Min(0)` 无上界 → 单次调用可认领全平台流。
- **PRF-09 / PRF-11（MEDIUM，规划隐患）**：`claimStreams` 的 O(capacity+N) 逐流 Redis 往返（renew=Lua/claim=SET NX 各一次往返），且整段循环跑在 `@Transactional(readOnly)` 内**跨外部 Redis IO 持 Hikari 连接**（PRF-02 反模式的 Redis 版）。当前非瓶颈，「放大即热点」。

## What Changes

- **ARC-01 抽 `CameraStreamInternalService`**：把 `getStreamCredential`/`listActiveStreamsForAi`/`claimStreams` 从 `CameraStreamService` 移入新的 `CameraStreamInternalService`（对齐 `DetectionIngestService` 先例）；`StreamCredentialController`/claim controller 改注入内部 service。**tenant CRUD 面与 AI-internal 面物理分离**，杜绝误加会话级方法到无鉴权类的 footgun。**HTTP 层鉴权（`hasRole("AI_SERVICE")`）与 wire 契约不变**。
- **SEC-05 补审计**：在凭据解密成功、claim 认领时经既有 `SecurityAuditWriter.record(...)` 写 `S1_EVIDENCE_READ`（含 deploymentId、streamId、结果；**绝不含明文凭据/token/PII**，遵守 invariant #5）。审计写入不得改变端点时延特征到破坏 claim 热路径（异步或短事务，参照既有 audit 写法）。
- **SEC-07 加 `@Max`**：`capacity` 加合理上界（可为常量或 `@Value` 配置，实现者给出可辩护的默认，如单栈 worker 上限量级），超限 → 400（`@Valid` 既有校验链）。
- **PRF-09 批量化 Redis**：把逐流 renew/claim 改为**批量**（pipeline 或多键 Lua），**严格保持原子性语义**——compare-and-renew 每键仍 `GET==owner then PEXPIRE`、认领仍 `SET NX EX` 每键至多一栈胜出，不得因批量弱化。
- **PRF-11 移 Redis 出事务边界**：`findActiveStreamsForAi()` 的 DB 读用短事务取回投影后**关闭事务**，Redis 循环在事务外执行，避免跨外部 IO 持 Hikari 连接。

## Non-goals（本轮明确排除，属决策门 C8）

- **不做 claim 端点分片作用域 / token→租户映射**（OQ-3 未拍板）——SEC-06 blast radius 维持「已接受」，本 change 只叠加 SEC-05 审计 + SEC-07 上界作为纵深防御，不改共享 token 模型。
- **不退役 Scheme A**（`GET /internal/streams` + `stream_registry.py`，ARC-03，属 OQ-3）。
- **不加 DB 索引**（PRF-10 需新增 V2 Flyway = schema 破坏性变更，须维护者逐个批准）——本 change 只做代码层 Redis 批量化 + 事务边界，不碰 schema。
- 不改 wire 契约（claim/lease/credentials 的 HTTP 请求响应结构不变，除 SEC-07 新增的 over-capacity 400）。
