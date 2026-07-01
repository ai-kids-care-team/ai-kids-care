## Context

`ai-ingest-detection-events-v1` 落地了 ADR-0015 V1 闭环全部代码，并定义了「单 AI 部署 = supervisor 枚举活跃流 + 每流一 worker 子进程（进程隔离、各自加载模型）」的形态（其 design D2）。核实的技术现状：

- `stream_live_alert_service.run_stream_service`（`ai/scripts/stream_live_alert_service.py:208-211`）**每进程**各自 `from_pretrained(...).to(device).eval()`，推理 `model(pixel_values=...)`（`:347`）为 **batch-of-1**。
- `:8001` 的 `Predictor`（`ai/src/ai_app/inference/predictor.py:49-51`）已是常驻加载模型的推理服务单例。
- 后端 `CameraStreamRepository#findActiveStreamsForAi()` JPQL `where cs.enabled = true` **无分发/均衡机制**，返回全库活跃流；`GET /api/v1/internal/streams`（`StreamCredentialController`）直接透传全集，`ROLE_AI_SERVICE` 共享 Bearer。
- Redis 已在栈内（Spring Session + 登录限流），具备 TTL 与原子 `SET NX`。
- 后端为**单实例**（维护者已定不做多实例横向扩展），故后端作为唯一协调者无分布式协调难题。

维护者 2026-07-01 决策：多 GPU 多 AI 栈；**流分发按池均衡、不按园所分片**（各园 CCTV 数量悬殊，按园所绑死会负载失衡）；单栈内 worker 池采用方案 A；跨栈分发采用 **Claim/Lease 动态租约池**；`MAX_WORKERS` 实测定；V1 只 assault；批准部署面变更但无 GPU 机器仍须能 `compose up`；拓扑为跨网异地（AI 栈随 GPU、mesh VPN 连后端）。

约束：多租户按 `kindergarten_id` 隔离由后端在 ingest 时从 `streamId` 推导（与哪台 GPU 在跑无关）；AI 绝不直连 DB；internal 端点 `ROLE_AI_SERVICE` Bearer、CSRF 豁免。

## Goals / Non-Goals

**Goals：**
- 跨 GPU 栈按容量均衡分发流：动态、自愈（宕机 failover）、加机零配置摊入。
- 单栈 worker 池有明确有界容量语义 + 超额显式信号。
- 给出可复现的 `MAX_WORKERS` 容量基准流程。
- GPU 直通不破坏无 GPU 环境的 `compose up` 与 CI。
- 收口 V1 assault-only 与分发/部署文档。

**Non-Goals：**
- 「解码器 + 共享批推理池」密度优化（方案 B）——独立 follow-up。
- token→租户绑定分片（被 Claim/Lease 取代）。
- 多类 `target_label`、对象存储 evidence、SSE 多实例 fanout、推理加速、模型/算法改动。

## Decisions

### D1：单栈 worker 池执行模型 = 有界进程池（每流一进程）+ 经验 `MAX_WORKERS`（方案 A）

沿用 ai-ingest D2 进程隔离，把「池」语义显式化：supervisor 持有一个**有界、自愈**进程池，`assigned_streams（本栈认领到的流）→ 池 → 每流一 worker`，上界 = `MAX_WORKERS`；崩溃退避重启、认领流集变化增删、一流崩不波及其它。

**为何不在本 change 做方案 B**：B 把推理层池化 + 跨流批处理（副本数 K≪N、吃满 GPU、单机承载翻倍级），但复杂度数量级跳升（IPC/批处理/背压/结果路由/共享推理器单点容错）；且 `MAX_WORKERS` 实测只在「每流一进程」下有意义——先按 A 拿真实密度，才知道 B 是否必要。**别在测量前做复杂优化。** 边界画法：worker 池对外只暴露「给定 `assigned_streams`，持续检测并 ingest」契约；B 替换的是池内部如何用 GPU，不动 claim/租约与 ingest 契约，故 B 可作纯 AI 侧 follow-up。

### D2：跨 GPU 栈流分发 = Claim/Lease 动态租约池（Redis）

把「所有活跃流」看成工作池，「所有 GPU 栈」是按容量认领的消费者。后端（单实例，协调者）持全集流 + Redis 租约表。

**端点** `POST /api/v1/internal/streams/claim`，body `{deploymentId, capacity, running: [streamId...]}` → resp `{assigned: [{streamId, modelId, kindergartenId}...]}`。后端逻辑：
1. **续租**：对 `running` 中仍活跃（`enabled=true`）且租约属主为 `deploymentId` 的流，compare-and-renew 刷新 TTL；已不活跃的不续租（→ 不在 assigned，supervisor 停之）。
2. **认领补位**：`spare = capacity - 已续租数`；若 `spare>0`，从**活跃且无有效租约（未认领或租约已过期）**的流里，原子 `SET stream_lease:{id} depId NX EX <ttl>` 认领至多 `spare` 个。
3. **返回** assigned = 续租 + 新认领。

**租约存储**：Redis `stream_lease:{streamId} = deploymentId`，`TTL = 数倍 poll 周期`（poll 20s → TTL 60s，容忍 1–2 次丢包）。原子认领 `SET NX EX`；续租用 Lua compare-and-renew（`if GET==depId then PEXPIRE`）确保只有属主能续，不误抢他栈租约。**无 Flyway 迁移**——租约是运行态、非权威数据，Redis 天然 TTL/原子契合。

**均衡 + 自愈**：
- 新摄像头 → 进池未认领 → 下轮被有空栈认领。
- 某栈宕机 → 停续租 → 租约 TTL 到期 → 相关流回未认领池 → 其它有空栈接管（failover + 再均衡）。
- 加一台 GPU → 一 claim 即认领空闲流，负载自动摊过去，**零配置**。
- 容量异构原生支持：强 GPU 设大 `MAX_WORKERS` 即多认领。
- 均衡质量：贪心「谁先有空谁认领」已足够均衡；如需更细，后端可优先分给负载最低者（V1 贪心即可）。

**GPU 栈身份**：各栈一个 `DEPLOYMENT_ID`（env），用于租约归属/续租；`AI_SERVICE_TOKEN` 继续共享（仅鉴权），身份与鉴权解耦。

**为何不用 token→租户映射（原方案，已废）**：那是 tenant-based 绑定分片，正是维护者否决的「按园所分导致失衡」；Claim/Lease 按容量而非租户分发，且无需 schema。

> 既有 `GET /api/v1/internal/streams`（ai-ingest 引入，返回全活跃集）：supervisor 不再据其驱动，改用 claim 端点。该 GET 可留作管理/诊断视图（`ROLE_AI_SERVICE`），或后续退役——本 change 不依赖它驱动分发。

### D3：GPU 直通只放 opt-in overlay + supervisor 归 compose profile

- 新增 `ai/docker-compose.gpu.yml`：**唯一**承载 `deploy.resources.reservations.devices`（nvidia），覆盖 `ai-inference` 与 `ai-live-supervisor`。
- base `ai/docker-compose.yml`：**无任何 GPU 硬要求**（现状即是）；无 GPU 机器 `compose up` 正常、CPU 回退、CI `Compose config`（`config -q`）照常过。
- `ai-live-supervisor` 加 `profiles: [<live>]`：无 GPU 机器默认 `up` 只起 `ai-inference`，避免 supervisor 缺 token/DEPLOYMENT_ID 时崩溃循环。
- GPU 主机：`docker compose -f ai/docker-compose.yml -f ai/docker-compose.gpu.yml --profile <live> up`。
- **铁律**：任何硬 GPU 要求只在 overlay，永不进 base——「无 GPU 机器不被卡死」的根本保证。根 `docker-compose.yml` 本就不含 AI 栈。

### D4：`MAX_WORKERS` 靠目标机基准实验测定

每 worker 各持一份模型副本 + 独立 CUDA context → **显存**是绑定约束。流程（目标 GPU 机、连真流或回放）：N=1 起逐步递增 worker，`nvidia-smi` 记录显存/GPU 利用率/单窗推理延迟/丢帧；取「利用率高、显存留 ~15% headroom、延迟不击穿告警实时性」的最大 N。随机型而异，记入 runbook；compose `MAX_WORKERS` 默认保守（如 2），生产按实测覆盖，作为 claim 的 `capacity`。

### D5：V1 检测目标 = assault，收口 interim 语义

状态机现状只盯 `assault`（12 类映射表存在但未全用）。spec 明确「V1 检测目标单类 `assault`，其余 later version」，关闭 ai-ingest OQ-5。无需改码。

## Risks / Trade-offs

- **[failover 瞬时双跑]** 某栈租约过期被另一栈接管时，若原栈只是慢（未真死）仍在跑该流 → 两栈短暂同跑同一流 → 重复 detection event。**Mitigation**：后端 `(kindergarten_id, dedup_key)` 幂等（ADR-0026）天然吸收重复；且 dedup_key 锚在告警起始时刻（ai-ingest D4），双跑产同 key → 去重。**安全，无需额外锁。** TTL 取数倍 poll 缩小双跑窗口。
- **[凭据端点越权]** `GET /internal/streams/{id}/credentials` 今按 id 取任意流。**Mitigation**：校验调用方当前持该流租约，否则 404。**本 change 纳入。**
- **[Redis 依赖成关键路径]** 租约在 Redis，Redis 挂则无法认领/续租。**Mitigation**：Redis 已是 Session/限流关键依赖，非新增单点；租约丢失时 worker 沿用「best-effort 不 crash」，Redis 恢复后下轮 claim 重建分配。
- **[单 GPU 密度低]** 方案 A 每流一副本 + N 份 CUDA context，单机承载有限 → 可能需较多 GPU。**Mitigation**：先实测密度；不足则上方案 B（独立 follow-up），契约边界已预留。
- **[跨网 internal 端点暴露]** 多栈跨网连后端，internal 端点公网暴露风险放大。**Mitigation**：mesh VPN（Tailscale/WireGuard）私网互联、token 经加密通道；必须公网则 Caddy TLS + IP allowlist。
- **[部署面 BREAKING]** 新 overlay + profile + DEPLOYMENT_ID。**Mitigation**：GPU 要求隔离于 overlay、supervisor 归 profile、逐项批准、`Compose config` CI 校验。

## Migration Plan

1. AI 侧：supervisor 改由 claim 驱动 + `DEPLOYMENT_ID` + 心跳续租 + 有界池（mock claim 端点单测，不动部署）。
2. 后端：`POST /internal/streams/claim`（续租/认领/返回）+ Redis 租约存取 + 凭据端点租约校验；契约测试覆盖「按容量认领、续租、过期再分配、跨租约凭据 404」。**无 schema。**
3. 部署：新增 `ai/docker-compose.gpu.yml` + supervisor `profiles` + `DEPLOYMENT_ID`；`config -q` 双路径（无 GPU base / GPU overlay）通过。
4. 文档/spec 收口（assault-only、Claim/Lease、GPU overlay、跨网 VPN runbook）。
5. `MAX_WORKERS` 基准脚本 + runbook（留维护者在 GPU 机执行）。
6. 回滚：claim 端点/Redis 租约/overlay/profile 均可回退；无破坏性数据变更。

## Open Questions

1. ~~**租约 TTL 与 poll 周期取值**~~ **已定（2026-07-01）：poll 20s / TTL 60s**（维护者采纳默认）。越短再均衡越快、越易误判慢栈为死；越长 failover 越慢——上线后可按实测微调，非阻塞。
2. **均衡策略**：V1 贪心「先到先认领」；是否需要后端「优先分给最低负载栈」以更平滑？（V1 可不做）
3. **既有 `GET /internal/streams` 去留**：留作诊断视图还是退役？
4. **方案 B 触发阈值**：实测每 GPU 承载流数低到何程度（GPU 成本）才启动共享批推理池 follow-up？
5. **凭据重取频率与轮转**：worker 多久重取一次流凭据、AES 版本轮转时如何不中断？
