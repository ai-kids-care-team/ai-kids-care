## Why

**现状修正（评审请先读）**：本提案立项前的预期是「AI 仍直发 Pushover/SMS demo、尚未接入 `POST /api/v1/internal/detection-events`」（见 `CLAUDE.md` interim 段）。**经核查代码，该前提已过时**——ADR-0015 V1 的 ingest 链路在 AI 与 backend **两侧均已实现并归档**：

- backend：`internal/DetectionSessionIngestController` + `DetectionEventIngestController`（`ROLE_AI_SERVICE` Bearer、`(kindergarten_id, dedup_key)` 幂等、可选 `evidence` 写 `event_evidence_files`）、SSE 推送、读 API、复核工作流、reconnect replay 均已就绪。
- AI：`ai/src/ai_app/utils/backend_ingest.py`（session/event + 有界重试/退避/10s 超时）、`event_type_mapper`、`build_dedup_key`、`severity_from_confidence`、`evidence_capture`，以及改造后的 `ai/scripts/stream_live_alert_service.py`（**已删除** Pushover/SMS 直发与 CSV）。
- `openspec/specs/ai-detection/spec.md` 已收录上述全部 requirement。

换言之，「写 ingest client」这件事**无需重做**。但**闭环在运行的栈里并未真正闭合**，原因是一个被忽略的运维缺口：

1. **没有任何进程在跑这条 ingest 链路。** AI 容器的部署入口是 `CMD ["python", "scripts/serve.py"]`，**只起 FastAPI 推理端点（:8001 的 `/health`、`/predict/upload`）**；`stream_live_alert_service.py` 是一个独立 `__main__`，**没有任何 compose service / 编排器启动它**（`ai/docker-compose.yml` 注释亦自承 ingest 三个 env「仅被 `stream_live_alert_service.py` 消费，不被 :8001 serving 路径使用」）。结果：运行中的系统里**没有任何实时检测产出**，`detection_events` 至今**全部来自 seed**——这与 `CLAUDE.md` 的现象描述一致，但根因不是「没写 client」，而是「写了没人跑」。
2. **没有多摄像头编排。** `STREAM_ID`/`MODEL_ID` 是单值 env，脚本只消费**一路**流；平台是多租户多摄像头（`camera_streams`），缺一个枚举活跃流并为每路流拉起/守护一个 worker 的 supervisor。后端也**没有**「列出 AI 应跑哪些流」的内部端点（只有按已知 id 取凭据的 `GET /api/v1/internal/streams/{id}/credentials`）。
3. **两处正确性缺陷**（在「跑起来」后才会显现）：① `dedup_key` 用提交时刻 `time.time()` 而非真实告警起始时刻，**重连后同一物理告警会生成不同 key → 后端写出重复 event**，违反 spec「同一 alarm 同 dedup key」场景；② event 的 `startTime`/`endTime` 都被填成 `now_iso`（零时长、墙钟当下），丢弃了状态机已知的真实告警窗口。
4. **文档与 spec 自相矛盾且过时**：`CLAUDE.md` interim 段、`ai-detection` spec 的「Current alert output (interim state)」requirement（断言「MUST NOT 写后端、只发 Pushover/CSV」）与已实现的「AI-side detection ingest client」requirement **直接冲突**，须收口。

本提案因此**不重做 ingest client**，而是把已经造好的闭环**真正运维起来**（部署 + 多流编排）并收掉上述正确性/文档缺口——这才是「关闭核心产品闭环」的真实剩余工作。

## What Changes

- **部署 live-detection worker（核心）**：让运行中的栈真正运行 ingest 循环——AI 子栈新增一个**长生命周期 worker 进程/服务**运行检测→ingest（与 :8001 FastAPI 推理端点**并存、互不替换**）。**BREAKING（部署面）**：改动 `ai/docker-compose.yml`（及生产 compose 叠加层），属部署变更，apply 前须经维护者批准。
- **多摄像头 supervisor**：新增一个 supervisor，枚举本部署应消费的活跃 `camera_streams`，为每路流拉起一个检测 worker，崩溃自动重启、流集合变化时增删 worker；每路流仍用既有 `STREAM_ID → 凭据 → URL`、`create_session → submit_event` 路径。**枚举来源已定为方案 A**（后端新增只读 `GET /api/v1/internal/streams`，见 design D3）。
- **修正 `dedup_key` 起始时刻**：在 `alarm_on` 跃迁瞬间捕获**墙钟告警起始时刻**并贯穿到 `build_dedup_key`，使重连/去抖的同一告警产生同 key（对齐 spec 既有场景，避免重复 event）。
- **修正 event 时间窗**：用状态机的真实告警窗口（起始..结束）填 `startTime`/`endTime`，而非 `now_iso`。
- **收口 interim 文档/spec**：修订 `ai-detection` spec 的「Current alert output (interim state)」requirement 为「已运维化」表述；更新 `CLAUDE.md` interim 段，使其反映「链路已实现、worker 已部署运行」。
- **不删除** `ai/src/ai_app/utils/pushover.py` / `sms.py`：核查发现二者仍被**训练脚本** `train.py`、`extract_binary_event_clips.py` 引用（训练进度通知），并非死代码——明确列为 Non-goal 以防误删。

**Non-goals（明确排除）**：
- **不重做**已实现并归档的 ingest client / backend ingest 端点 / evidence / 重试 / severity / SSE / 读 API / 复核工作流。
- 不做对象存储（S3/MinIO）evidence 上传（仍写本地 `file://`）。
- 不做 SSE 多实例 fanout（Redis pub/sub）。
- 不做 GPU 自动扩缩容 / 推理加速；不引入新 ML 模型或改检测算法（持久化规则、阈值、`target_label` 保持现状）。
- 不删除 `pushover.py`/`sms.py`（仍被训练脚本使用）。
- 不改检测/复核→家长通知的下游（已闭环）。

## Capabilities

### New Capabilities
<!-- 无新增能力命名空间；worker/supervisor 与正确性/文档收口均落在既有 ai-detection 能力内。 -->

### Modified Capabilities
- `ai-detection`：ADDED「已部署的 live-detection worker 与多摄像头 supervisor」requirement（运行中的栈 SHALL 持续运行检测→ingest 循环，按活跃流 fan-out + 守护重启）；ADDED「dedup_key 取自真实告警起始时刻」与「event 时间窗取自真实告警窗口」requirement；MODIFIED「Current alert output (interim state)」requirement，由「只发 Pushover/CSV、不写后端」收口为「经 backend ingest，无 Pushover/SMS/CSV」的已运维化表述。

## Impact

- **AI 端代码**：新增 supervisor（`ai/scripts/` 或 `ai/src/ai_app/`）；改 `stream_live_alert_service.py`（告警起始时刻贯穿 `dedup_key`、真实时间窗、可被 supervisor 调用的入参）；`ai/.env.example`（单流→多流配置）。
- **部署**：`ai/docker-compose.yml` 新增 worker/supervisor service（与 :8001 并存）；生产 compose 叠加层相应改动——**部署变更，需维护者逐项批准**。
- **backend（已定，必做）**：新增内部只读端点 `GET /api/v1/internal/streams`（`ROLE_AI_SERVICE`、`@Hidden`、租户域由后端把控、不返回解密凭据）供 supervisor 枚举活跃流（维护者裁定方案 A，见 design D3）。无 schema 迁移。
- **文档/spec**：`openspec/specs/ai-detection/spec.md` 增量；`CLAUDE.md` interim 段更新。
- **测试**：AI 端 pytest（dedup_key 起始时刻、时间窗、supervisor 流枚举/重启逻辑，injectable mock 无 torch/av/真实流）；`compose-config` CI 校验新 service。
- **风险**：本机无法跑真实 FLV 流 + 模型权重，运行时验证靠单测 + 后端 docker 集成；GPU 容量对「每路流一个模型副本」的承载是未决项（见 Open Questions）。
