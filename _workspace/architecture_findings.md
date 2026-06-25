# Architecture Findings (architecture-analyst, sonnet)

- ARC-01 [high｜backend] 外部 HTTP 调用（Pushover/SMS）在 `@Transactional` 内 — `NotificationService.dispatch()` 持库连接做网络 IO；推成功但后续 save 失败回滚 → status 卡 SENDING。已被 staff/guardian 告警路径在生产调用。`NotificationService.java:134-178`。修：事务外发或 outbox。
- ARC-02 [high｜backend] SSE emitter 注册表在进程内 `ConcurrentHashMap`，多实例下 A 实例事件到不了 B 实例客户端。`DetectionEventSseService.java:32`。修：Redis pub/sub 扇出。
- ARC-03 [high｜backend] `@Scheduled`（DeferredNotificationScanner / SSE heartbeat）无分布式锁，多实例重复发推/竞态。`DeferredNotificationScanner.java:20`。修：ShedLock。
- ARC-04 [medium｜backend] N+1：`AppreciationLetterService.buildVO`（每行 2 查询）、`KindergartenAdminApprovalService.listPendingRegistrations`（stream 内每项 1 查询）。修：JOIN FETCH / 批量预载。
- ARC-05 [medium｜backend] `DetectionIngestService` 用 JdbcTemplate autocommit 写、JPA 读，双数据访问抽象；加列时易 drift。修：迁移门禁/契约测试。
- ARC-06 [high(confidence)｜backend] `@EnableAsync` 无自定义 `TaskExecutor` → 默认 `SimpleAsyncTaskExecutor` 无界线程。`AiKidsCareApplication.java:15`。修：有界 `ThreadPoolTaskExecutor`。
- ARC-07 [medium｜cross] Neo4j 全量部署（容器+1G 堆+data-loader+healthcheck depends_on）但唯一使用者 `GraphService` `@PreAuthorize("denyAll()")` 死代码。修：feature flag 门控或补完。
- ARC-08 [medium｜backend] `GuardianNotificationService` AFTER_COMMIT `@Async` 监听 RESOLVED 路径用 `getReferenceById` 代理，未来加 SMS 易触发 LazyInitializationException。修：统一批量载全实体。
- ARC-09 [low｜ai] `/predict/upload` 先整块 `await file.read()` 入内存再校验大小，并发上传易 OOM。`app.py:104`。修：分块流式写临时文件。
- ARC-10 [low｜backend] `NotificationService.listNotifications(keyword,pageable)` 未发布重载调 `findAll` 无租户/鉴权范围，误暴露即跨租户泄露。修：删除该重载。

Top-3：ARC-01（事务内外部调用+投递原子性）、ARC-02+03（横向扩展即坏）、ARC-07（Neo4j 死代码却全量部署）。
