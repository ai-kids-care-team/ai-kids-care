## Context

ADR-0015 V1 闭环（AI 检测 → backend ingest → SSE + 通知；backend 为 `detection_events` 唯一写入者，ADR-0026）的**全部代码已实现并归档**：

- **backend 侧**（`com.ai_kids_care.v1.internal`）：`DetectionSessionIngestController`（`POST /api/v1/internal/detection-sessions`，body `{streamId, modelId}` → `{sessionId}`，后端从 stream 推导 `kindergarten_id`/`camera_id`）、`DetectionEventIngestController`（`POST /api/v1/internal/detection-events`，`DetectionEventIngestRequest{sessionId, eventType, severity, confidence, startTime, endTime, dedupKey, status?, evidence?}`，`(kindergarten_id, dedup_key)` 幂等、可选 `evidence` 写 `event_evidence_files`）。鉴权由 `AiServiceTokenAuthenticationFilter` + `SecurityConfig` 的 `hasRole("AI_SERVICE")` 在 HTTP 层强制；`/api/v1/internal/**` 是唯一 CSRF 豁免前缀。
- **AI 侧**（`ai/`）：`utils/backend_ingest.py`（`create_session`/`submit_event`，有界重试 3 次 + 指数退避 + 10s 超时，token 不入日志）、`utils/event_type_mapper.py`（VideoMAE label → `EventTypeEnum`，未知 → `OTHER`）、`build_dedup_key`、`severity_from_confidence`（分档规则）、`utils/evidence_capture.py`（写 `file://` 片段 + SHA-256）、`scripts/stream_live_alert_service.py`（持久化规则状态机 + `alarm_on` → session/event ingest，**已删除** Pushover/SMS/CSV）、`utils/stream_credentials.py`（`STREAM_ID` → `GET /api/v1/internal/streams/{id}/credentials` → 解密 URL）。

**真正缺口**——见 proposal：(1) 部署入口 `CMD ["python", "scripts/serve.py"]` 只起 :8001 FastAPI 推理端点，没有任何 compose service / 编排器启动 `stream_live_alert_service.py`，**运行中的栈无任何实时检测产出**；(2) 脚本单流单值 `STREAM_ID`，无多摄像头 supervisor，后端也无「列流」端点；(3) `dedup_key`/时间窗取自提交时刻 `now` 而非真实告警窗口；(4) interim 文档/spec 与已实现行为矛盾。

约束：本机无 Java、无 GPU、无真实 FLV 流；多租户隔离由后端按 `kindergarten_id` 把控，AI 只送 `streamId`（后端推导租户），AI worker 绝不直连 DB。

## Goals / Non-Goals

**Goals:**

- 让运行中的栈**真正运行**检测→ingest 循环：AI 子栈以长生命周期进程运行 worker（与 :8001 推理端点并存）。
- 多摄像头：一个 supervisor 按活跃流 fan-out，每路流一个检测 worker，崩溃重启、流集合变化时增删。
- 收正确性：`dedup_key` 与 event 时间窗取自真实告警起始/结束时刻。
- 收文档：interim 段（`CLAUDE.md` + `ai-detection` spec）与已实现行为对齐。

**Non-Goals:**

- 重做 ingest client / backend 端点 / evidence / 重试 / severity / SSE / 读 API / 复核（均已实现归档）。
- 对象存储 evidence、SSE 多实例 fanout、GPU 扩缩容/推理加速、新检测模型或算法改动。
- 删除 `pushover.py`/`sms.py`（仍被训练脚本引用）。

## Decisions

### D1：worker 与 FastAPI 推理端点**并存**，不互相替换

`serve.py`（:8001 `/predict/upload` 等同步推理）服务的是「按需对单段视频推理」用例；live-detection worker 服务的是「长流式消费 + 状态机告警 + ingest」用例。二者职责不同、生命周期不同（请求响应 vs. 常驻）。**决策**：保留 :8001 不动，新增独立 worker 进程。**备选**（否决）：把流消费塞进 FastAPI lifespan/后台任务——会把推理 API 的可用性与流 worker 的崩溃耦合，且 FastAPI 单进程不适合 N 路流的 CPU/GPU 阻塞解码。

### D2：supervisor + 每路流一个 worker 子进程（进程隔离）

`stream_live_alert_service.run_stream_service(...)` 已是「单流、可重连、不 crash」的完整单元，且其重活（PyAV 解码 + torch 推理）是 CPU/GPU 密集的阻塞调用。**决策**：supervisor 为每个 `streamId` 拉起一个**子进程**跑 `run_stream_service`，监控存活并按退避重启；进程隔离使一路流的崩溃/内存泄漏不波及其它流，也规避 Python GIL 对并行解码的限制。**备选**：单进程多线程（受 GIL 限制、一处段错误全崩）或 asyncio（PyAV/torch 是同步阻塞，async 无收益）——否决。supervisor 自身保持极薄（只做拉起/守护/增删），不含 ML 依赖，便于单测。

### D3：活跃流枚举来源——**已定：方案 A（后端「列流」端点）**（维护者 2026-06-30 裁定）

supervisor 需知道「本部署应跑哪些 `streamId`（及各自 `modelId`）」。今天 `STREAM_ID` 是单值手注，后端只有「按 id 取凭据」无「列出活跃流」。

- **方案 A（已选）**：backend 新增内部端点 `GET /api/v1/internal/streams`（`ROLE_AI_SERVICE`，返回该 AI 部署应消费的活跃流 `[{streamId, modelId, kindergartenId?}]`），supervisor 周期性拉取并 reconcile。理由：单一真源（`camera_streams`）、新增/下线摄像头自动生效、与多租户「后端把控可见集」一致。代价：后端一个只读端点（无 schema 迁移）——见 tasks 第 4 节（必做）。
- **方案 B（已否决）**：配置驱动 env `STREAM_IDS=1,2,3`。零后端改动，但摄像头增删需改部署配置、人工维护、易漂移——在多园所下很快变成运维负担，故否决。

**方案 A 不改 schema、不让 AI 直连 DB**：端点只读、租户域由后端把控、不返回解密凭据（凭据仍走既有 `/{id}/credentials`）。

### D4：`dedup_key` 与时间窗取自真实告警起始/结束

现状 `stream_live_alert_service.py` 在 `alarm_on` 分支用 `onset_epoch = time.time()`（提交时刻）建 key，并把 `startTime`/`endTime` 都填 `now_iso`。**问题**：① 重连后 `state` 重置、同一物理告警重新 `alarm_on` → 新 `time.time()` → 不同 `dedup_key` → 后端写重复 event（违反 spec「同一 alarm 同 dedup key」）；② event 时间窗退化为零时长墙钟当下，dashboard 失真。**决策**：在 `alarm_on` 跃迁的那一刻捕获**墙钟告警起始时刻**（`datetime.now(timezone.utc)` 于跃迁瞬间，连同状态机已有的 `alarm_start_sec`/`ts_sec` 流内相对窗），`build_dedup_key(stream_id, alarm_onset_wall_epoch)` 用该起始秒；`startTime` = 告警起始墙钟、`endTime` = 当前窗墙钟（或 cooldown 内的窗口末端）。注意：`build_dedup_key` 已是秒精度 `{streamId}-{epochSec}`，契约不变，仅**喂入正确的时刻**。

> 重连同告警是否仍能产生同 key 受限于「跨重连能否复原同一墙钟起始秒」——见 Risks。本 change 至少把「**同一进程内**同告警同 key」做对，并尽量缩小重连重复窗口；彻底的重连去重可能依赖后端时间窗去重，列为 Open Question。

### D5：interim 文档/spec 收口用 MODIFIED 而非 REMOVED

`ai-detection` spec 的「Current alert output (interim state)」requirement 与已实现的「AI-side detection ingest client」矛盾。**决策**：MODIFIED 该 requirement，重写为「检测结果经 backend ingest，不再发 Pushover/SMS、不再写 CSV；worker 已部署运行」的稳定表述（保留「interim」历史语义的收束，而非整段删除丢历史）。同步更新 `CLAUDE.md` interim 段。

### D6：失败/降级语义沿用既有「best-effort、绝不 crash」

worker 内 `create_session`/`submit_event` 已是有界重试 + 耗尽后 log 放弃 + 不 crash；supervisor 在 worker 进程退出时按退避重启。**决策**：不引入跨进程持久化失败队列（仍非目标）；后端不可达时该路流的本轮 event 丢失但流服务自愈，符合 spec 既有「Exhausted ingest retries do not crash」。

## Risks / Trade-offs

- **[GPU/CPU 容量]** 每路流一个 worker 子进程各自 `from_pretrained` 加载一份模型 → N 路流 = N 份模型副本 + N 路解码。单 GPU/单机能承载的 N 未知 → 可能 OOM/抖动。**Mitigation**：V1 先支持小 N（单租户/少摄像头）；容量模型、模型副本共享、批量推理列为 follow-up + Open Question；supervisor 暴露 `MAX_WORKERS` 上限保护。
- **[重连去重不彻底]** D4 修正「同进程同告警同 key」，但跨重连复原同一墙钟起始秒不保证 → 极端重连仍可能产生相近但不同的 `dedup_key`。**Mitigation**：把 onset 锚到 `alarm_on` 跃迁瞬间而非每窗；如仍不足，向后端提「按 `(kindergarten_id, camera_id, 时间窗)` 的二级去重」Open Question（不在本 change 实现）。
- **[部署面 BREAKING]** 改 `ai/docker-compose.yml` + 生产叠加层引入常驻 worker，影响资源占用与发布。**Mitigation**：worker 与 :8001 解耦为独立 service，可独立伸缩/下线；compose 变更逐项经维护者批准；`compose-config` CI 校验。
- **[枚举端点新增攻击面]** 方案 A 的 `GET /api/v1/internal/streams` 暴露流清单。**Mitigation**：`ROLE_AI_SERVICE` + `@Hidden`，与既有 internal 端点同等约束；只读、不含解密凭据（凭据仍走既有 `/{id}/credentials`）。
- **[本机不可端到端验证]** 无 GPU/真实流。**Mitigation**：supervisor/dedup/时间窗逻辑用 injectable mock 单测；端到端留后端 docker + 手动起 worker 的集成验证。

## Migration Plan

1. AI 端先落 supervisor + D4 正确性修正（纯代码，单测护航，不动部署）。
2. （若选方案 A）backend 加只读列流端点（无 schema 迁移）。
3. compose 变更（worker/supervisor service）——**维护者逐项批准**后落地；`compose-config` CI 通过。
4. 文档/spec 收口（D5）。
5. 回滚：worker/supervisor 是新增 service，回滚即移除该 service，:8001 推理端点与既有 ingest 端点不受影响；无 schema 迁移、无破坏性数据变更。

## Open Questions

1. ~~**流枚举方案 A vs. B（D3）**~~ **已定（2026-06-30）：方案 A**，后端新增只读 `GET /api/v1/internal/streams`；tasks 第 4 节转为必做。
2. **本 AI 部署的租户/流范围**：单一 AI 部署是否服务所有租户的所有摄像头，还是按租户/园所分片部署多个 AI 栈？影响 supervisor 枚举与 `MAX_WORKERS`。
3. **GPU 容量与模型副本**：单机/单 GPU 能并行承载几路 VideoMAE 流？是否需要共享模型副本或集中推理服务（复用 :8001）而非每 worker 各加载一份？
4. **`MODEL_ID` 的每流归属**：当前单值 env=1；多流多模型时，`modelId` 由列流端点随流返回，还是另有 camera→model 映射？
5. **`target_label` 单类**：状态机当前只盯 `assault`，12 类映射表存在但未全用——V1 是否只检测 assault？多类告警是否本期范围外？
6. **重连去重彻底性（D4/Risk）**：是否需要后端补一层基于时间窗的二级去重，以覆盖跨重连无法复原同一 onset 秒的情况？
7. **worker 凭据获取频率与轮转**：流凭据 AES 版本化可轮转；supervisor/worker 多久重取一次凭据、轮转时如何不中断？
