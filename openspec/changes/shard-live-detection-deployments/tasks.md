# Tasks — shard-live-detection-deployments

> 依赖：本 change 排在 `ai-ingest-detection-events-v1` 之后（精化其 supervisor / 列流端点）。
> 破坏性/gated 项（部署面变更）apply 前须维护者**逐项批准**。**无 schema 迁移**（租约用 Redis）。

## 1. 维护者决策与批准（前置）

- [x] 1.1 批准部署面变更（新增 `ai/docker-compose.gpu.yml` + supervisor `profiles` + `DEPLOYMENT_ID`）——维护者 2026-07-01 已批准
- [x] 1.2 租约 TTL / poll 周期初值已定：**poll 20s / TTL 60s**（design OQ-1，维护者采纳默认值）
- [x] 1.3 分发模型已定：Claim/Lease 动态租约池（按容量均衡，非按园所），Redis 存租约无 schema
- [x] 1.4 单栈执行模型已定：方案 A 有界进程池 + MAX_WORKERS 实测；方案 B 列独立 follow-up

## 2. 后端：Claim/Lease 工作分发

- [ ] 2.1 `POST /api/v1/internal/streams/claim`（`ROLE_AI_SERVICE`，`@Hidden`）：body `{deploymentId, capacity, running[]}` → `{assigned:[{streamId, modelId, kindergartenId}]}`
- [ ] 2.2 续租：对 `running` 中仍 `enabled=true` 且租约属主==deploymentId 的流，Lua compare-and-renew 刷新 TTL
- [ ] 2.3 认领补位：`spare = capacity - 已续租`；从活跃且无有效租约的流原子 `SET NX EX` 认领至多 spare 个
- [ ] 2.4 Redis 租约存取：key `stream_lease:{streamId}=deploymentId`，TTL 可配；复用既有 `RedisConnectionFactory`
- [ ] 2.5 `CameraStreamRepository` 辅助查询：活跃流全集 / 供认领筛选（`enabled=true`，谓词进 JPQL）；`modelId` 由 service 解析随流返回
- [ ] 2.6 凭据端点租约校验：`GET /internal/streams/{id}/credentials` 校验调用方持该流租约，否则 404（隐藏存在性）
- [ ] 2.7 后端契约测试：按容量认领上限、续租保持、租约过期后被他栈再认领、跨租约凭据 404、`ROLE_AI_SERVICE` 外 4xx、并发认领无重复分配（SET NX 原子）
- [ ] 2.8 `./gradlew test` 全套件绿（DooD 串行），零回归

## 3. AI：supervisor 改由 claim 驱动 + 有界 worker 池

- [ ] 3.1 `ai_app/supervisor`：周期性 claim（带 `DEPLOYMENT_ID`、`capacity=MAX_WORKERS`、`running=当前在跑流集`），claim 兼作心跳续租
- [ ] 3.2 据 claim 返回的 assigned 集 reconcile 有界进程池（新增拉起/移除停掉/崩溃退避重启，不波及其它）
- [ ] 3.3 不静默丢弃：assigned 与本地在跑不一致、或 claim 失败时逐条 `log`（后端已按容量分配，正常不超上界）
- [ ] 3.4 `DEPLOYMENT_ID` env + fail-fast（缺失报错，与 `AI_SERVICE_TOKEN` 同规）
- [ ] 3.5 AI 单测（injectable mock claim 端点，无 GPU/无真流）：认领→拉起、续租、失去租约→停 worker、claim 失败重试不 crash、崩溃重启
- [ ] 3.6 `cd ai && PYTHONPATH=src python -m pytest tests/ -v` 通过

## 4. AI：MAX_WORKERS 基准实验

- [ ] 4.1 基准脚本/文档：目标 GPU 机 N 递增 + `nvidia-smi` 采集显存/利用率/延迟/丢帧
- [ ] 4.2 runbook 记录判定（利用率高 + 显存留 ~15% headroom + 延迟不击穿实时性 → 最大 N），该值即 claim 的 capacity
- [ ] 4.3 端到端实测取值（**留维护者在 GPU 机执行**）

## 5. 部署：GPU overlay + profile（gated）

- [ ] 5.1 新增 `ai/docker-compose.gpu.yml`：唯一承载 nvidia 设备预留，覆盖 `ai-inference` + `ai-live-supervisor`
- [ ] 5.2 `ai/docker-compose.yml` 给 `ai-live-supervisor` 加 `profiles: [<live>]` + `DEPLOYMENT_ID` env；base 保持无 GPU 依赖
- [ ] 5.3 验证两路径 `config -q` 均过：无 GPU base（默认只起 ai-inference）+ GPU overlay+profile
- [ ] 5.4 确认无 GPU 机 `docker compose -f ai/docker-compose.yml up` 不被 GPU 要求卡死；根 compose 仍不含 AI 栈

## 6. 文档 / spec 收口

- [ ] 6.1 `ai-detection` spec 增量：Claim/Lease 分发 + worker 池有界容量/超额信号 + GPU overlay 不破坏无 GPU + V1 assault-only（ADDED，层叠 ai-ingest）
- [ ] 6.2 `CLAUDE.md` AI 段补「Claim/Lease 动态租约池 + GPU overlay + profile」形态
- [ ] 6.3 部署 runbook：跨网 mesh VPN（`JAVA_BACKEND_URL` 经加密通道）+ GPU 主机准备（NVIDIA Container Toolkit + cu130 轮子驱动兼容性验证）+ `DEPLOYMENT_ID` 分配
- [ ] 6.4 关闭 ai-ingest design OQ-2（租户/流范围）、OQ-5（assault-only）；引用本 change

## 7. 验证与收尾

- [ ] 7.1 `openspec validate shard-live-detection-deployments --strict` 通过
- [ ] 7.2 端到端集成验证（**留维护者在 GPU + 真流 + 多栈环境**）：两栈按容量均分流、宕机后租约过期被接管、加机自动摊入、瞬时双跑经后端幂等去重无重复 event
- [ ] 7.3 Non-goal 边界复核：方案 B（共享批推理池）未混入、接口边界为其预留；记入 follow-up 清单
