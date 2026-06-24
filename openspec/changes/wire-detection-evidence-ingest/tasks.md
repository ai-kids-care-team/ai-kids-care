## 1. DTO

- [ ] 1.1 新增嵌套 `record EvidenceFile(@NotBlank String uri, @NotBlank String hash, @NotNull EvidenceFileTypeEnum type, @NotNull MimeTypeEnum mimeType)`(与 `DetectionEventIngestRequest` 同文件/同包)。
- [ ] 1.2 给 `DetectionEventIngestRequest` 增加可选字段 `@Valid EvidenceFile evidence`(无 `@NotNull`,保持可选;`@Valid` 触发嵌套校验实现 all-or-nothing)。

## 2. 持久化(TDD)

- [ ] 2.1 先写失败的集成测试(Testcontainers,扩展 `BaseIntegrationTest`):ingest 带 evidence → `event_evidence_files` 落一行且字段正确(`storage_uri/hash/type/mime_type/hold=false/retention_until=null`)。先看红。
- [ ] 2.2 在 `DetectionIngestService.ingestEvent()` 拿到 `eventId` 后、`@Async` staff alert 之前,`req.evidence() != null` 时 `INSERT INTO event_evidence_files (...) VALUES (..., ?::evidence_file_type_enum, ?, ?::mime_type_enum, false, ?)`。看 2.1 转绿。
- [ ] 2.3 补测试:ingest 不带 evidence → 事件照常落、无 evidence 行;红→绿(应直接绿,因可选)。
- [ ] 2.4 补测试:幂等重复 ingest(同 `(kindergarten_id, dedup_key)`)带 evidence → 返回既有 `event_id`、不写第二条 evidence 行。确认实现走幂等早返回路径(自然不插)。
- [ ] 2.5 补测试:evidence 缺字段 / 未知 `type`/`mimeType` enum → `400`,事件与 evidence 均不写。

## 3. 验证

- [ ] 3.1 后端 DooD 全套件回归(`gradle:8.7-jdk21`,`cleanTest test`,host override/ryuk-disabled/挂 repo 根),0 fail。
- [ ] 3.2 自查:无 schema 迁移文件新增;对不带 evidence 的 ingest 行为零变化(向后兼容)。
