## Context

ADR-0015 V1 闭环里,evidence 视频**不进 PostgreSQL**:AI 把视频写到文件系统/对象存储(`file://`,可升级 `s3://`),在 ingest payload 里带上 `evidence_uri`+`evidence_hash`,由**后端**据此写一行 `event_evidence_files`(spec `ai-detection`:「Detection ingest REST endpoints (V1)」「Detection closed-loop target architecture」)。

现状(已由只读核查确认,含 file:line):
- `event_evidence_files` 表**已存在**(`db/initdb/01_create_schema.sql:426`,镜像于 `V1__initial_baseline.sql`):列为 `evidence_id`(IDENTITY PK)、`event_id`、`kindergarten_id`、`type`(`evidence_file_type_enum`: `IMAGE`/`VIDEO`)、`storage_uri`、`mime_type`(`mime_type_enum`: `image/jpeg`/`image/png`/`video/mp4`)、`created_at`、`retention_until`(nullable)、`hold`(boolean **NOT NULL**)、`hash`。实体 `EventEvidenceFile.java`、enum 类、controller 壳、seed 均在。
- ingest 事件 DTO `DetectionEventIngestRequest.java:17` 仅有 `sessionId/eventType/severity/confidence/startTime/endTime/dedupKey/status`,**无 evidence 字段**。
- `DetectionIngestService.ingestEvent()`(`DetectionIngestService.java:45`)用 `JdbcTemplate` 写 `detection_events`(`:59` INSERT … RETURNING event_id),service **非 `@Transactional`**(每条 INSERT autocommit,以便 `@Async` staff alert 立刻看到已提交事件);幂等检查(`:51`)命中重复时提前返回既有 `event_id`。
- spec「AI-side detection ingest client」结尾明确:evidence(`evidence_uri`/`evidence_hash` 与 `event_evidence_files` 写入)是该 slice 的 out-of-scope——即一个**已知未实现**的 open requirement。

## Goals / Non-Goals

**Goals:**
- 后端 ingest 能**可选**接收 evidence 并落 `event_evidence_files`,且对不带 evidence 的请求 100% 向后兼容。
- 零 schema 迁移(复用既有表/enum/实体)。
- evidence 仅在**新事件**路径写入,幂等重复不重复写。
- 未知 enum 值以 `400` 拒绝,和既有 `event_type` 校验风格一致。

**Non-Goals:**
- AI 侧真正写视频 / 算 hash / 发 evidence(独立 follow-up,在 ai/ 仓)。
- evidence 保留策略 / `retention_until` 计算 / `hold` 法务保留工作流。
- 把 evidence 推/流给前端、看板播放 evidence。
- 单次 ingest 携带多个 evidence 文件(本期一事件一文件)。
- 后端校验文件真实存在或 hash 与文件内容匹配(AI 受信,`ROLE_AI_SERVICE`;见 Risks 信任边界)。

## Decisions

**D1 — DTO 形状:嵌套可选 record,而非平铺字段。**
`DetectionEventIngestRequest` 增一个可选字段 `EvidenceFile evidence`;`EvidenceFile` = `record EvidenceFile(@NotBlank String uri, @NotBlank String hash, @NotNull EvidenceFileTypeEnum type, @NotNull MimeTypeEnum mimeType)`。整字段为 `null` 表示「无 evidence」;非 `null` 时用 `@Valid` 触发内部 `@NotBlank/@NotNull`,实现「全有或全无」。
- 为什么不平铺四个可空字段:平铺无法表达「要么四个都给、要么都不给」,且校验分散。嵌套 record 用一个 `@Valid` 就把 all-or-nothing 收口。
- `type`/`mimeType` 直接用既有 enum 类型,Jackson 反序列化未知值即 `400`,与 `event_type` 现有处理一致,无需手写校验。

**D2 — 写入方式:沿用 `JdbcTemplate`,在 `ingestEvent()` 新事件路径内插入。**
在 `:68` 拿到 `eventId` 之后、`:79` 触发 `@Async` staff alert 之前,若 `req.evidence() != null` 则执行一条 `INSERT INTO event_evidence_files (event_id, kindergarten_id, type, storage_uri, mime_type, hold, hash) VALUES (?, ?, ?::evidence_file_type_enum, ?, ?::mime_type_enum, false, ?)`。
- 为什么 jdbc 而非 JPA 实体保存:与 `DetectionIngestService` 既有写法一致(同一非事务、autocommit 风格);避免在非 `@Transactional` service 里混用 JPA 持久化上下文。
- 只在新事件路径写:幂等重复在 `:51` 已提前 return,自然不会重复插 evidence。

**D3 — 一事件一 evidence。** DTO 携带单个 `evidence` 对象。AI V1 一次 alarm 产出一段视频→单条。多文件留作未来(数组化),本期 Non-Goal。

**D4 — `hold`/`retention_until` 取默认。** `hold=false`(表列 NOT NULL 无默认,必须显式给);`retention_until=null`(保留策略 out-of-scope)。

## Risks / Trade-offs

- **[事件已落、evidence 插入失败(非事务)]** → service 非 `@Transactional`,event 与 evidence 是两次 autocommit;若 evidence INSERT 失败,会留下「有事件、无 evidence 行」。缓解:evidence 是补充信息,事件本身(告警闭环主链)不应因 evidence 失败而回滚;evidence 插入失败记日志、best-effort。真要原子可后续给这对写入包一个显式事务(本期不做,避免改动既有 autocommit 语义影响 `@Async` 可见性)。
- **[信任边界:不校验文件/hash]** → 后端不验证 `storage_uri` 指向的文件真实存在、也不重算 hash 比对。调用方是 `ROLE_AI_SERVICE`(Bearer),受信内网链路;后端只忠实记录 AI 提供的 uri/hash。若未来 evidence 对外可取用,再加完整性校验。记为 follow-up。
- **[enum 受限]** → `mime_type_enum` 仅 `image/jpeg`/`image/png`/`video/mp4`。AI 若产出其他容器(如 `video/webm`)会被 `400`。本期与既有 enum 对齐;扩容器是独立 schema change。

## Migration Plan

无 schema 迁移。纯增量代码 + 测试。回滚 = 还原两个文件改动;不带 evidence 的 ingest 不受影响,无数据迁移。

## Open Questions

- evidence 写入是否要与事件写入做成单事务(原子)?本期按 best-effort,见 Risks。
- `retention_until` 的保留策略由谁定(园级配置?固定 N 天?)——独立 follow-up。
