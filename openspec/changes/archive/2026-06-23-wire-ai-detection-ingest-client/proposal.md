## Why

闭环步骤④。①②③ 已让「后端复核 → 家长通知」端到端贯通,但 `detection_events` 目前**只来自 seed**(演示数据);真实 AI 检测层(`ai/`,Python)仍是 demo 状态——实时流告警**直发 Pushover/SMS + 写本地 CSV,不写后端**(`stream_live_alert_service.py`)。`ai-detection` spec(ADR-0015 V1)要求 AI 经**后端 internal ingest 端点**提交,后端为 detection 表 sole writer;Pushover/SMS demo 代码 MUST be replaced。

后端 ingest 端点(步骤①)**已就绪**:`POST /api/v1/internal/detection-sessions`(streamId+modelId→sessionId)、`POST /api/v1/internal/detection-events`(sessionId/eventType/severity/confidence/start/end + AI 生成 dedupKey,幂等),Bearer `AI_SERVICE_TOKEN`,kindergarten/camera/room 后端从 stream 推导。

本 change(④)实现 **AI 端 ingest client**:stream 连接成功 → 建 session;`alarm_on` → 提交 event;替换 demo 直发。真实 AI 检测由此接入闭环(AI alarm → 后端 `detection_events` → staff 告警 → 复核 → 家长通知)。

## What Changes

- **新建 `ai/src/ai_app/utils/backend_ingest.py`**:HTTP client(injectable callable 模式,仿 `stream_credentials.py`,便于 pytest mock)封装 `create_session(streamId, modelId) → sessionId` 与 `submit_event(sessionId, eventType, severity, confidence, startTime, endTime, dedupKey) → {eventId, duplicate}`;`Authorization: Bearer {AI_SERVICE_TOKEN}`;base URL `JAVA_BACKEND_URL`。
- **新建 event_type 映射**(`event_type_mapper.py`):VideoMAE 12 类 label → `EventTypeEnum` 字符串(按 spec 映射表;未知/不在表 → `OTHER`)。
- **新建 dedup_key 生成**:基于 stream/camera + alarm-onset 时刻(同一 alarm 同 key,重连/去抖不产生重复 event,对齐后端 `(kindergarten_id, dedup_key)` 幂等)。
- **改造 `ai/scripts/stream_live_alert_service.py`**:stream 连接成功后建 session(存 `session_id`);`alarm_on`(过 cooldown)时 `submit_event`(eventType 经映射);**移除** Pushover/SMS 直发调用、CSV open/write/flush/close、相关 import 与函数参数;ingest 失败 best-effort(log,不崩流服务)。
- **`ai/.env.example`**:删 `PUSHOVER_*`/`SOLAPI_*`/`SMS_*`;保留/补 `JAVA_BACKEND_URL`/`AI_SERVICE_TOKEN`/`STREAM_ID`/`MODEL_ID`。
- **pytest**:`test_backend_ingest.py`(session/event 调用、dedup_key、event_type 映射、Bearer header 注入;injectable mock,无真实网络,仿 `test_stream_credentials.py`)。

Non-goals(defer / 后续):

- **evidence**(`evidence_uri`/`evidence_hash` + AI 写检测视频到文件系统 + hash + 后端写 `event_evidence_files`):**后端 `DetectionEventIngestRequest` DTO 当前也缺这两个字段**,是跨端独立工作;④ 的 event ingest **不带 evidence**,记 follow-up(④' evidence)。
- **不改后端**(ingest 契约已就绪);**无 schema 迁移**。
- 不做 SMS 端口(⑤,后端侧);前端看板(⑥);删除 `ai/utils/pushover.py`/`sms.py` 文件本身(本期移除其调用,文件删除可一并或随 SMS-端口决策定)。

## Capabilities

### Modified Capabilities

- `ai-detection`: ADDED「AI-side detection ingest client」—— AI 端 stream start→session、`alarm_on`→event 提交,VideoMAE label→`EventTypeEnum` 映射,AI 生成 dedup_key,移除 Pushover/SMS 直发 + CSV demo。实现 ADR-0015 V1 的 AI 侧(后端侧步骤①已就绪)。

## Impact

- **AI 端代码**(Python):新 `backend_ingest`/`event_type_mapper`/dedup_key + 改 `stream_live_alert_service.py` + `.env.example`。
- **测试**:`ai/tests/` pytest(injectable mock,轻量依赖,无 torch/av);CI `ai-tests.yml`。
- **无后端改动、无 schema 迁移、无 Java 改动。**
- **解锁**:真实 AI 检测端到端接入闭环;evidence(④')与 SMS 端口(⑤)在此之上增量。
- **风险**:本机无法跑真实 FLV 流 + 模型权重 → 运行时验证靠 pytest 单元(ingest client mock);真实端到端(后端 docker + AI 调)留集成验证。
