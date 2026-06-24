## Why

闭环 ④(AI 端 alarm → 后端 ingest)落地后,AI 端仍有四处 loose ends:evidence 后端契约(`3af66f8`)已就绪但 **AI 端从不发送 evidence**;`severity_from_confidence` 是标注 "interim" 的线性占位映射;`notification_title` 是移除 Pushover/SMS 后残留的死参数;ingest 失败是纯 best-effort 丢弃(无重试/缓冲),一次后端抖动就永久丢事件。本 change 收掉这四项,让 AI→ingest 这条边达到生产质量。

## What Changes

- **evidence AI 端实写**:告警触发时,从帧缓冲抽取片段写为本地 `video/mp4` 文件并算 SHA-256,在 `submit_event` 的 payload 里带上后端已就绪的 `evidence` 描述符(`{uri, hash, type: "VIDEO", mimeType: "video/mp4"}`,all-or-nothing)。后端无需改动。
- **severity 映射定稿**:把 `severity_from_confidence` 从 "interim" 线性映射改为按置信度分档的明确业务规则,docstring 去掉 interim 标注;并修正调用点传参语义(当前传的是目标类概率 `target_prob`)。
- **清理死参数 `notification_title`**:从 `run_stream_service` 签名、`__main__` 赋值与传参处移除(Pushover/SMS 时代遗留,函数体从不读取)。
- **ingest 失败有界重试**:`create_session` / `submit_event` 失败时按有界次数 + 退避重试;耗尽后仍 best-effort 放弃并 log(绝不 crash 流服务),把「一次抖动即永久丢」收窄为「短暂抖动可恢复」。

非目标(Non-goals):后端 evidence DTO/持久化(已在 `3af66f8` 完成);对象存储(S3/MinIO)上传(本期写本地 `file://`,远端存储另议);跨进程持久化失败队列(本期为进程内有界缓冲);多实例。

## Capabilities

### New Capabilities
<!-- 无新增能力;均为既有 ai-detection 能力的行为补全。 -->

### Modified Capabilities
- `ai-detection`: AI 端 ingest 行为补全 —— SHALL 在告警时产出 evidence 片段(uri+hash)并随 ingest 发送;severity 映射 SHALL 为已定稿的分档规则(非 interim);ingest 失败 SHALL 在放弃前做有界重试(仍不得 crash 流服务)。

## Impact

- 代码:`ai/src/ai_app/utils/backend_ingest.py`(evidence 参数、severity 规则、重试)、`ai/scripts/stream_live_alert_service.py`(产出片段+hash、接入重试、删 `notification_title`)、可能新增 `ai/src/ai_app/utils/evidence_capture.py`。
- 契约:复用 develop 上已就绪的后端 `DetectionEventIngestRequest.EvidenceFile`(`internal` 包),无后端/schema 改动。
- 测试:`ai/tests/test_backend_ingest.py` 扩展(evidence payload、severity 规则、重试),新增 evidence_capture 单测;docker `python:3.12` 跑 pytest。
- spec:`openspec/specs/ai-detection/spec.md` 中 ingest/evidence/severity 相关 requirement 增量。
