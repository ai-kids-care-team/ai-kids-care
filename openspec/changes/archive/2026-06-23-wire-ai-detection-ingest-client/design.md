## Context

闭环步骤④,纯 AI 端(`ai/`,Python)。已核实:

- **后端 ingest 契约就绪**(步骤①):
  - `POST /api/v1/internal/detection-sessions` body `{streamId, modelId}` → `{sessionId}`;后端从 `camera_streams WHERE stream_id=?` 推导 `(kindergarten_id, camera_id)`。
  - `POST /api/v1/internal/detection-events` body `{sessionId, eventType, severity, confidence, startTime, endTime, dedupKey, status?}` → `{eventId, duplicate}`;`(kindergarten_id, dedup_key)` 幂等(重复返回 `duplicate=true`);后端从 session→camera→`room_camera_assignments` 推 `room_id`,异步触发 staff 告警。
  - 鉴权:`Authorization: Bearer {AI_SERVICE_TOKEN}`(`ROLE_AI_SERVICE`,仅 `/api/v1/internal/**`)。
  - `EventTypeEnum`:`ASSAULT/FIGHT/BURGLARY/VANDALISM/SWOON/WANDER/TRESPASS/DUMP/ROBBERY/DATEFIGHT/KIDNAP/DRUNKEN/OTHER`。
- **AI 端现状**:`stream_live_alert_service.py` 已读 `JAVA_BACKEND_URL`/`AI_SERVICE_TOKEN`/`STREAM_ID`,已用 `stream_credentials.py`(injectable `http_get`)调后端 credential 端点;`alarm_on` 滑窗状态机(60s 窗 hit≥8 且 ratio≥0.5 且 span≥30s);`alarm_on/off` 是内部状态标记(非 EventTypeEnum);predictor 返回 label 字符串;直发 Pushover/SMS + 写 CSV。**无 ingest client、无 label→enum 映射、无 dedup_key**。
- **evidence**:AI 端无视频写文件代码,且后端 `DetectionEventIngestRequest` DTO **无** `evidence_uri/hash` 字段 → ④ defer。
- **pytest**:轻量依赖(CI 不装 torch/av/requests/dotenv,用 `sys.modules` stub);`test_stream_credentials.py` 的 injectable `http_get` mock 是 ingest client 测试范本。

## Goals / Non-Goals

**Goals:** AI 端经后端 internal ingest 提交 session/event,替换 demo 直发;event_type 映射 + dedup_key;pytest 覆盖。

**Non-Goals:** evidence(④');删 pushover.py/sms.py 文件本身;SMS 端口(⑤);前端;改后端;真实流端到端(留集成验证)。

## Decisions

### D1:ingest client = injectable HTTP callable(仿 stream_credentials)
`backend_ingest.py` 暴露 `create_session(stream_id, model_id, *, http_post=...)` 与 `submit_event(session_id, event_type, severity, confidence, start_time, end_time, dedup_key, *, http_post=...)`。默认 `http_post` 用 `requests.post`(运行时),测试注入 mock(无网络)。从 env 读 `JAVA_BACKEND_URL`/`AI_SERVICE_TOKEN`,Bearer header,JSON body 用后端 DTO 的驼峰字段名(streamId/modelId/sessionId/eventType/...)。非 2xx → 抛/返回错误,调用方 best-effort 处理。

### D2:event_type 映射
`event_type_mapper.map_label(label) -> str`:按 spec 映射表(VideoMAE label → EventTypeEnum),大小写规范化;不在表 → `"OTHER"`。集中单函数 + 表常量,便于测试。

### D3:dedup_key 生成
`f"{stream_id}-{int(alarm_onset_epoch)}"`(或含 camera)。alarm-onset 时刻 = 本次 alarm_on 转换的起始时刻 → 同一 alarm 窗内重复触发/重连产生**同一 key**,后端幂等去重。精度到秒足够(alarm 有 cooldown)。

### D4:stream_live_alert_service 改造
- stream 连接成功后**建 session 一次**:`session_id = create_session(STREAM_ID, MODEL_ID)`;失败 log + 继续(无 session 则后续 event 跳过或重试,best-effort)。
- `alarm_on`(过 cooldown)→ `submit_event(session_id, map_label(target_label), severity, confidence, start, end, dedup_key)`;失败 log,不崩流服务。
- 移除:`send_pushover_notifications`/SMS 调用、CSV open/write/flush/close、`from ai_app.utils.pushover/sms import`、相关函数参数。
- persistence 状态机 + predictor 不变(回归靠现有 `test_persistence.py`)。

### D5:severity 来源(Open → 暂定)
后端 event 需 `severity Integer`。AI 端暂定 `severity = max(1, min(5, round(confidence * 5)))`(confidence 档位 1–5)。可演进为 per-event_type 风险分。apply 第 2 步定;记 Open Question。

### D6:modelId 来源
session 需 `modelId`(指向 `ai_models` 行)。AI 端从 `MODEL_ID` env 配置(默认指向 seed 的 active 模型行)。不在 AI 端查 ai_models(后端 sole reader)。

### D7:evidence defer
不实现 `evidence_uri/hash` + 视频写文件;event ingest 不带 evidence。后端 DTO 也缺该字段 → ④' 同时补后端 DTO + `event_evidence_files` 写 + AI 视频/hash。记 follow-up。

## Risks / Trade-offs

- **[本机无法跑真实流]** 无 FLV 流 + 模型权重 → 运行时只能 pytest 单元(ingest client/映射/dedup mock)。真实端到端(后端 docker + AI 调 internal 端点)留集成验证 + 部署环境。
- **[stream_live_alert_service 改造大]** 移除 demo + 接 ingest;回归靠 `test_persistence.py`(状态机不变)+ 新 `test_backend_ingest.py`。改造谨慎,保 alarm 逻辑不变。
- **[dedup_key 对齐]** 必须保证同一 alarm/重连同 key(后端幂等);用 alarm-onset 秒级时刻。
- **[best-effort ingest]** 后端不可达时 log + 不崩流服务;丢失的 event 不重发(本期无队列;记 follow-up)。
- **[severity 暂定]** confidence 档位是占位,可能不匹配业务严重度语义;Open Question。

## Migration Plan

1. **[TDD]** `backend_ingest.py`(create_session/submit_event,injectable http_post)+ `test_backend_ingest.py`(调用 payload/header、错误处理)。
2. **[TDD]** `event_type_mapper.py` + dedup_key(映射表、未知→OTHER、key 格式)。
3. 改造 `stream_live_alert_service.py`:接 session/event ingest,移除 Pushover/SMS/CSV。
4. `.env.example` 清理。
5. `ai/` pytest 全绿(`PYTHONPATH=src python -m pytest tests/ -v`);CI `ai-tests.yml`。
6. `ai-detection` spec delta;code review;archive + 合 develop + push。
- **回滚**:git 还原 `ai/`;无后端/schema 改动。

## Open Questions

- **severity** 怎么定?confidence 档位(D5 暂定)vs per-event_type 固定风险分。apply 第 2 步定。
- **modelId** 默认值/来源(`MODEL_ID` env 指向哪个 ai_models 行)。
- **evidence defer 确认**(④' 补后端 DTO + AI 视频)。
- ingest 失败的**重试/缓冲**(本期 best-effort 丢弃)是否够,还是需本地队列?记 follow-up。
- `pushover.py`/`sms.py` 文件**删除时机**(本期移除调用;文件删除随 ⑤ SMS-端口决策,因 SMS 要在后端重做)。
