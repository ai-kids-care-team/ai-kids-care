## Why

`openspec/specs/ai-detection/spec.md` 已经规定后端是 `event_evidence_files` 的唯一写入方,并要求 AI 把 `evidence_uri`/`evidence_hash` 放进 ingest payload、由后端落 `event_evidence_files` 行(ADR-0015 V1)。但当前后端 ingest 的事件 DTO `DetectionEventIngestRequest` **完全没有 evidence 字段**,`DetectionIngestService` 也从不写 `event_evidence_files`——这条契约只在 spec 里、代码里没有落点。

存放 evidence 的 `event_evidence_files` 表 / 两个 enum / `EventEvidenceFile` 实体 / controller 壳 / seed **早在 V1 baseline 就已存在**,因此本 change **零 schema 迁移**,只补后端这一半:让 ingest 能接收 evidence 并落库。AI 侧真正写视频/算 hash 仍是独立 follow-up(另仓)。

## What Changes

- 给 `DetectionEventIngestRequest` 增加**可选**的 evidence 字段(嵌套 `EvidenceFile { uri, hash, type, mimeType }`,全有或全无校验)。AI 当前不发 evidence,故必须保持可选——缺省时 ingest 行为完全不变。
- `DetectionIngestService.ingestEvent()` 在**新事件**(非重复)持久化成功后,若请求带 evidence,则写入一行 `event_evidence_files`(`event_id` 关联刚插入的事件,`storage_uri`=uri、`hash`=hash、`type`、`mime_type`、`hold=false`、`retention_until=null`)。命中幂等重复(`(kindergarten_id, dedup_key)` 已存在)时**不**重复写 evidence。
- 未知 `type`/`mime_type` enum 值按现有 `event_type` 的处理方式以 `400` 拒绝。
- 无 schema 迁移、无新表、无新依赖。

## Capabilities

### New Capabilities
<!-- 无新增 capability。 -->

### Modified Capabilities
- `ai-detection`: 新增一条 requirement,规定后端在 detection-event ingest 上**接收可选 evidence 并写入 `event_evidence_files`**;evidence 缺省时事件照常持久化、不产生 evidence 行。现有「AI-side detection ingest client」requirement 不变(AI 侧仍不发 evidence,其 out-of-scope 说明依然成立)。

## Impact

- `backend/src/main/java/com/ai_kids_care/v1/internal/DetectionEventIngestRequest.java`(加可选嵌套 evidence)。
- 新增 `EvidenceFile` 嵌套 record(放在 DTO 同包或同文件)。
- `backend/src/main/java/com/ai_kids_care/v1/service/DetectionIngestService.java`(`ingestEvent()` 新事件路径插入 `event_evidence_files`)。
- 复用既有表 `event_evidence_files` + `evidence_file_type_enum` + `mime_type_enum` + `EventEvidenceFile` 实体。
- 测试:新增 ingest-with-evidence 的集成测试(Testcontainers)。
- 非破坏:无 schema 迁移、无 API 路径变化、对不带 evidence 的现有 AI ingest 100% 向后兼容。
