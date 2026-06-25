# Performance / Scalability Findings — backend — VERIFIED

Verifier: finding-verifier (adversarial static, standard tier). 每条 1 票，默认假设假阳性并尝试反驳。
仅复核 severity ∈ {high, medium}（PRF-01..04 high；PRF-05/06/07 medium）；PRF-08 low 标 skipped。
注：原文多条 confidence=medium 并建议 DooD 实测线程数/连接数/SELECT 计数坐实。本机无 JVM/容器 → 这些「运行时倍数」无法动态坐实；但其**前提条件**（缺 Executor bean / 缺超时 / 缺锁 / 事务边界 / LAZY 无 fetch）可静态确证，故对「缺陷存在」判 confirmed，对「运行时具体放大倍数」标注「未动态坐实」。

---

```yaml
- id: PRF-01
  severity: high
  title: "@EnableAsync 无自定义 Executor → 退化到无界 SimpleAsyncTaskExecutor"
  verification:
    verdict: confirmed
    method: static
    votes: 1
    note: >
      反驳失败。AiKidsCareApplication.java:15 确有 @EnableAsync；build.gradle 与 config/ 全树 grep
      AsyncConfigurer|ThreadPoolTaskExecutor|getAsyncExecutor|taskExecutor = 0；application.yml 无
      spring.task.execution.* → 确无自定义 Executor，Spring 默认退化 SimpleAsyncTaskExecutor（每任务新线程）。
      @Async 使用方已坐实：StaffAlertService.alertForEvent:55、GuardianNotificationService.onEventReviewed:62、
      DetectionEventSseService.onIngested:66 三处均跑该默认池。缺陷成立。运行时「线程随事件线性膨胀至 OOM」的
      具体倍数未动态坐实（需 DooD 压测）。
- id: PRF-02
  severity: high
  title: "外部投递（Pushover/SMS）位于 @Transactional 边界内 → 持库连接做网络往返 + 原子性缺口"
  verification:
    verdict: confirmed
    method: static
    votes: 1
    note: >
      反驳失败。NotificationService.dispatch:134 @Transactional；外部 pushoverService.sendToUser:168 夹在
      repository.save(145) 与 save(174) 之间；dispatchSms 同构（smsPort.send:199 在事务内）。代码注释:159-166
      自承 delivery-atomicity gap（推已发但 SENT save 失败 → 回滚卡 SENDING）。事务内外部 IO 与原子性缺口均坐实。
      「高峰耗尽连接池阻塞」未动态坐实。
- id: PRF-03
  severity: high
  title: "Pushover / Solapi 外部客户端无连接/读取超时 → 无界阻塞"
  verification:
    verdict: confirmed
    method: static
    votes: 1
    note: >
      反驳失败。PushoverClientConfig:16-18 = new PushoverRestClient()（SDK 默认，无超时）；SolapiClientConfig:19
      = NurigoApp.INSTANCE.initialize(...)（SDK 默认，无 connect/read timeout）。两处确以默认构造、未配超时。
      叠加 PRF-02（事务内）则一次慢调用占一连接+一线程成立。「几十秒吃光 10 连接」的实测未动态坐实。
- id: PRF-04
  severity: high
  title: "DeferredNotificationScanner @Scheduled 无分布式锁 → 多实例重复投递"
  verification:
    verdict: confirmed
    method: static
    votes: 1
    note: >
      反驳失败。DeferredNotificationScanner:31 @Scheduled scan() 拉 DEFERRED 行后逐条 dispatch（41-43），无
      @SchedulerLock；build.gradle grep shedlock|ShedLock|net.javacrumbs = 0（依赖未引入）；类注释:20 自承
      single-instance、ShedLock 为 follow-up。dispatch 内 status 翻转为「先查后改」非原子、SELECT 无 FOR UPDATE
      SKIP LOCKED → 仅靠 status 不能去重。多实例重复投递缺口成立。与项目 MEMORY「多实例 defer」一致。
```

```yaml
- id: PRF-05
  severity: medium
  title: "SSE emitter 注册表为进程内 ConcurrentHashMap → 多实例时事件到不了别实例客户端"
  verification:
    verdict: confirmed
    method: static
    votes: 1
    note: >
      反驳失败。DetectionEventSseService:32 emittersByKindergarten = new ConcurrentHashMap<>()（进程内）；
      onIngested:68-69 只读本进程注册表；事件源 DetectionIngestService.publishEvent 为本地 ApplicationEvent
      （非跨进程）。多实例下 ingest 命中 B、SSE 连在 A → A 收不到实时增量成立。finding 自评 medium（有 replaySince
      DB 重放兜底，非永久丢失）合理，未夸大。
- id: PRF-06
  severity: medium
  title: "DetectionEvent 列表 / SSE 重放走 LAZY @ManyToOne + 无 JOIN FETCH → N+1"
  verification:
    verdict: confirmed
    method: static
    votes: 1
    note: >
      反驳失败（并精校倍数）。DetectionEvent.java:37-49 四个 @ManyToOne(LAZY)；Mapper 访问 kindergarten.name/
      cctvCameras.cameraName/rooms.name（14-18）触发懒加载——注意 .id 经 @JoinColumn 不触发懒载，故每行
      ~3 条额外 SELECT（与 finding「~3」吻合）；DetectionEventRepository 列表/replaySince 查询无 JOIN FETCH/
      @EntityGraph。N+1 结构成立。「20 行→~60 次 / replay 200→~600 次」的实测倍数未动态坐实（需 show-sql 计数）。
- id: PRF-07
  severity: medium
  title: "HikariCP 未显式配置（默认 max 10）+ SSE 长连接占 servlet 线程 → 连接/线程双瓶颈"
  verification:
    verdict: confirmed
    method: static
    votes: 1
    note: >
      反驳失败。application.yml grep hikari|maximum-pool|task|execution|async.request-timeout = 0 命中 → 池大小
      未配（Hikari 默认 max 10），无 spring.task.execution.*、无 mvc.async.request-timeout；SSE STREAM_TIMEOUT_MS
      =30min（DetectionEventSseService:29）每连接占一异步槽。容量盲区成立。「突发投递占满 10 连接阻塞」的实测未
      动态坐实。属容量规划缺口（与 PRF-01/02/03 同根，可在报告归并为「未配置无界资源」一束）。
```

```yaml
- id: PRF-08
  severity: low
  verification: { verdict: skipped, note: "low — 未复核（超出 high+medium 范围）" }
```

---

## 复核备注
- 7 条 high+medium 全 confirmed，0 refuted。所有「缺陷存在」的静态前提均逐一以源码/grep 坐实；无一为定位错误或演示误报。
- 覆盖与局限（供报告）：本机无 JVM/容器，**运行时放大倍数（线程膨胀、连接池耗尽时序、N+1 实际 SELECT 计数）未动态坐实**——这些是 DooD 项，深度档应实跑（原文「验证建议」已列 PRF-01/02-03/06 三组实测方案）。confirmed 限于「缺陷条件成立」，不含「实测严重度」。
- 结构归并提示：PRF-01/03/07 同属「异步/外部 IO/连接资源均未配上界」一束；PRF-04/05 同属「@Scheduled 与 SSE 的多实例缺位（缺 ShedLock / Redis pub-sub）」一束——lead 综合时可按束收口，避免读者把 7 条当 7 个独立工程。
