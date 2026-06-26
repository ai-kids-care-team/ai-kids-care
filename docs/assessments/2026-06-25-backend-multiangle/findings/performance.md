# Performance / Scalability Findings — backend

分析对象：`C:\ai-kids-care\backend`（develop 57ad6e5）。视角：运行时行为与扩展瓶颈（N+1 / 事务内 IO / 线程池 / SSE 背压 / 多实例去重 / 缓存 / 延迟）。本机无 JVM/容器运行 → 多数为静态推断（confidence=medium），可实测项（线程数、查询计数、延迟）标注交 finding-verifier DooD 坐实。

---

```yaml
- id: PRF-01
  angle: performance
  component: backend
  severity: high
  title: "@EnableAsync 无自定义 Executor → 退化到无界 SimpleAsyncTaskExecutor（每任务起新线程）"
  location: backend/src/main/java/com/ai_kids_care/AiKidsCareApplication.java:15
  evidence: |
    @SpringBootApplication
    @EnableAsync          // ← 无 AsyncConfigurer / 无 ThreadPoolTaskExecutor @Bean
    @EnableScheduling
    public class AiKidsCareApplication { ... }
    # 全工程 grep AsyncConfigurer|ThreadPoolTaskExecutor|@Bean.*Executor|getAsyncExecutor → No matches
    # @Async 使用方：StaffAlertService.alertForEvent(每次 ingest 触发)、
    #   GuardianNotificationService.onEventReviewed、DetectionEventSseService.onIngested
  description: |
    Spring 的 @EnableAsync 在无显式 Executor bean 时，@Async 默认走 SimpleAsyncTaskExecutor——
    它对每个任务新建一条线程、无队列、无上限、无拒绝策略（仅有可选 concurrencyLimit，未设=无限）。
    本工程每条 AI 检测事件 ingest 都会异步触发 StaffAlertService.alertForEvent，且 SSE onIngested、
    Guardian 通知监听器也都跑在该默认池上。检测事件是高频突发流量（多摄像头并发告警），峰值时
    线程数随事件数线性膨胀 → 线程上下文切换风暴、内存压力、最终 OOM / 拒绝服务。这是典型「单实例
    低量正常、放大即爆」的扩展瓶颈。
  recommendation: |
    定义一个有界 ThreadPoolTaskExecutor（core/max/queueCapacity 显式）作为 @Async 默认执行器
    （实现 AsyncConfigurer.getAsyncExecutor 或命名 @Bean("taskExecutor")），并设
    CallerRunsPolicy 或显式拒绝策略做背压。SSE 推送与告警投递可分池隔离，避免相互饿死。
  confidence: medium
  cross_refs: [PRF-02, PRF-05]
```

```yaml
- id: PRF-02
  angle: performance
  component: backend
  severity: high
  title: "外部投递（Pushover/SMS）位于 @Transactional 边界内 → 持库连接做网络往返 + 投递原子性缺口"
  location: backend/src/main/java/com/ai_kids_care/v1/service/NotificationService.java:134
  evidence: |
    @Transactional
    public void dispatch(Notification notification) {
        ...
        notification.setStatus(SENDING); repository.save(notification);   // 持有 DB 连接
        pushoverService.sendToUser(...);  // ← 外部 HTTP 调用在事务内（PushoverRestClient）
        notification.setStatus(SENT); ... repository.save(notification);
    }
    # dispatchSms 同构：smsPort.send(...) 也在同一 @Transactional 内（NotificationService.java:188-206）
    # 代码注释自承："Delivery-atomicity gap ... If the push succeeds but the subsequent SENT save
    #   fails, the transaction rolls back and the row is left at SENDING (the push already went out)"
  description: |
    dispatch() 整个方法 @Transactional，外部 Pushover/SMS 网络调用夹在两次 repository.save 之间。
    两个代价：(1) 一条 DB 连接被占用整个网络往返时长（Pushover/Solapi 无超时配置，见 PRF-03），
    HikariCP 默认仅 10 连接（application.yml 未配 pool size），高峰投递会迅速耗尽连接池，阻塞所有
    其他请求；(2) 投递原子性缺口——推已发出但后续 SENT save 失败则事务回滚，行卡在 SENDING（注释
    已自认）。dispatch 被 StaffAlertService、GuardianNotificationService、DeferredNotificationScanner
    在循环里逐收件人调用 → 放大效应：N 个收件人 × 网络往返各占一条连接。
  recommendation: |
    将外部投递移出事务：先在短事务内置 SENDING 并提交 → 事务外做 HTTP 调用 → 再开短事务回写
    SENT/FAILED（或采用 outbox 模式 + 投递 worker）。给外部调用加超时（PRF-03）。引入幂等键
    使「推已发、状态写失败」可安全重试而不重复推送。
  confidence: high
  cross_refs: [PRF-03, PRF-06, PRF-01]
```

```yaml
- id: PRF-03
  angle: performance
  component: backend
  severity: high
  title: "Pushover / Solapi 外部客户端无连接/读取超时 → 无界阻塞拖垮线程与连接池"
  location: backend/src/main/java/com/ai_kids_care/v1/config/PushoverClientConfig.java:16 ↔ backend/src/main/java/com/ai_kids_care/v1/config/SolapiClientConfig.java:19
  evidence: |
    // PushoverClientConfig
    @Bean public PushoverClient pushoverClient() { return new PushoverRestClient(); } // 无超时
    // SolapiClientConfig
    return NurigoApp.INSTANCE.initialize(config.getApiKey(), config.getApiSecret(), SOLAPI_API_DOMAIN); // 无超时
    # 两处均用 SDK 默认构造，未设 connect/read timeout
  description: |
    PushoverRestClient 与 Solapi DefaultMessageService 均以 SDK 默认值构造，未配置连接/读取超时。
    当 Pushover/Solapi 端点慢或挂起时，dispatch() 的外部调用会无限期阻塞——叠加 PRF-02（调用在
    事务内），一条阻塞调用 = 一条 DB 连接 + 一条 @Async 线程被无限期占用。突发告警下，慢的第三方
    会在几十秒内吃光 10 连接的 HikariCP 与无界 @Async 池（PRF-01），把整个后端拖入不可用。无超时
    的外部调用是延迟热点的头号来源。
  recommendation: |
    给两个客户端注入显式 connect/read timeout（如各 3s/5s）。若 SDK 不暴露，则包一层带超时的
    HttpClient/RestClient，或在投递 worker 上用 CompletableFuture.orTimeout 兜底。配超时是把
    PRF-02 连接占用从「无界」降到「有界」的前提。
  confidence: high
  cross_refs: [PRF-02]
```

```yaml
- id: PRF-04
  angle: performance
  component: backend
  severity: high
  title: "DeferredNotificationScanner @Scheduled 无分布式锁 → 多实例重复投递通知"
  location: backend/src/main/java/com/ai_kids_care/v1/service/DeferredNotificationScanner.java:31
  evidence: |
    @Scheduled(fixedDelayString = "...:60000", initialDelayString = "...:60000")
    public void scan() {
        List<Notification> due = notificationRepository
            .findByStatusAndDeferredUntilLessThanEqual(DEFERRED, OffsetDateTime.now());
        for (Notification n : due) { notificationService.dispatch(n); }
    }
    # 类注释自承："Single-instance deployment assumed — ShedLock multi-instance dedup is a follow-up."
    # build.gradle grep shedlock|ShedLock|net.javacrumbs → No files found（依赖未引入）
  description: |
    scan() 每 60s 拉「已到期的 DEFERRED 通知」并逐条 dispatch。无 ShedLock/任何分布式锁。多实例
    部署（部署环境为 watchtower CD，水平扩展可期）下，N 个实例会同时各自跑 scan()，对同一批
    DEFERRED 行并发 dispatch → 同一通知被推送多次给家长（quiet-hours 结束后家长被重复轰炸），且
    多实例对同一行的 SENDING→SENT 写存在竞态。这是「现在单实例能跑、加实例即坏」的去重缺口，且
    直接造成用户可见的重复告警。dispatch 内 status 翻转不是原子声明（先查后改），SELECT 也无
    FOR UPDATE SKIP LOCKED，故仅靠 status 不能自然去重。
  recommendation: |
    引入 ShedLock（@SchedulerLock，已记入项目 follow-up）或对 due 集合用
    SELECT ... FOR UPDATE SKIP LOCKED 抢占式领取 + 乐观状态 CAS（DEFERRED→SENDING 条件更新，
    受影响行数=0 则跳过）。后者还能顺带做多实例分摊。
  confidence: high
  cross_refs: [PRF-02]
```

```yaml
- id: PRF-05
  angle: performance
  component: backend
  severity: medium
  title: "SSE emitter 注册表为进程内 ConcurrentHashMap → 多实例时事件到不了别实例的客户端"
  location: backend/src/main/java/com/ai_kids_care/v1/service/DetectionEventSseService.java:32
  evidence: |
    private final Map<Long, Set<SseEmitter>> emittersByKindergarten = new ConcurrentHashMap<>();
    @Async @EventListener
    public void onIngested(DetectionEventIngestedEvent event) {
        Set<SseEmitter> set = emittersByKindergarten.get(event.kindergartenId());
        if (set == null || set.isEmpty()) return; // 只看本进程注册表
        ...
    }
    # 类注释自承："in-process; single-instance assumption — multi-instance fanout via Redis pub/sub
    #   is a follow-up"。事件源 DetectionIngestService.publishEvent 是本地 ApplicationEvent（非跨进程）。
  description: |
    emitter 注册表与事件分发都在单进程内。多实例部署下：家长/教师的 SSE 长连接落在实例 A，而某条
    检测事件的 ingest POST 命中实例 B → B 发布的本地 ApplicationEvent 只触达 B 自己注册表（空），
    A 上的看板客户端永远收不到该实时事件。看板「实时性」在水平扩展后静默退化为「只在巧合同实例时
    生效」。注：SSE 重连 replaySince 能补漏（event_id 游标查 DB），所以事件不会永久丢失，但实时
    增量在跨实例时失效——降级为「靠重连/轮询补」。故定 medium（非 high：有 DB 重放兜底）。
  recommendation: |
    多实例化前，将事件分发改为经 Redis pub/sub（已在 Redis 栈内）或消息总线广播
    DetectionEventIngestedEvent 到所有实例，各实例再 fan-out 到本进程 emitter；或在边缘做 sticky
    session 把同园连接固定到同实例（次优）。
  confidence: medium
  cross_refs: [PRF-04]
```

```yaml
- id: PRF-06
  angle: performance
  component: backend
  severity: medium
  title: "DetectionEvent 列表 / SSE 重放走 LAZY @ManyToOne + 无 JOIN FETCH → N+1 查询（重放路径放大至 ~600 次/重连）"
  location: backend/src/main/java/com/ai_kids_care/v1/entity/DetectionEvent.java:37 ↔ backend/src/main/java/com/ai_kids_care/v1/mapper/DetectionEventMapper.java:13
  evidence: |
    // 实体：4 个 LAZY @ManyToOne
    @ManyToOne(fetch = LAZY) private Kindergarten kindergarten;
    @ManyToOne(fetch = LAZY) private CctvCamera cctvCameras;
    @ManyToOne(fetch = LAZY) private Room rooms;
    @ManyToOne(fetch = LAZY) private DetectionSession detectionSessions;
    // mapper 逐项访问其中 3 个的标量（触发懒加载）
    @Mapping(source = "kindergarten.name", target = "kindergartenName")
    @Mapping(source = "cctvCameras.cameraName", target = "cameraName")
    @Mapping(source = "rooms.name", target = "roomName")
    // repository 无 JOIN FETCH / @EntityGraph
    Page<DetectionEvent> findByKindergarten_Id(Long kindergartenId, Pageable pageable);
    List<DetectionEvent> findByKindergarten_IdAndIdGreaterThanOrderByIdDesc(...);  // replaySince 用
  description: |
    DetectionEventMapper.toVO 读取 kindergarten.name / cctvCameras.cameraName / rooms.name，三者皆
    LAZY，而列表/重放查询无 JOIN FETCH 也无 @EntityGraph。结果：每条事件额外触发 ~3 条 SELECT。
    影响面：(1) GET /detection-events 列表 20 行 → 1+~60 次查询；(2) SSE 重连 replaySince 上限
    replayMax=200，单次重连最坏 200×3 ≈ 600 次查询（看板批量重连/部署滚动重启时同园大量客户端同时
    重连 → 查询风暴打 DB）；(3) getForPush 单条 +3。open-in-view=false + 方法级 @Transactional 使
    懒加载在事务内完成（无 LazyInit 异常），所以是纯性能 N+1 而非功能 bug。重放路径的放大使其值得
    修而非忽略。
  recommendation: |
    给三个读路径加 @EntityGraph(attributePaths={"kindergarten","cctvCameras","rooms"}) 或 JPQL
    JOIN FETCH（分页场景注意 fetch join + 分页的 Hibernate 内存分页告警，可改投影 DTO 查询直接选
    所需标量列，规避实体懒加载）。replaySince 优先改投影查询（它本就只取少数列映射 VO）。
  confidence: medium
  cross_refs: []
```

```yaml
- id: PRF-07
  angle: performance
  component: backend
  severity: medium
  title: "HikariCP 连接池未显式配置（默认 max 10）+ SSE 长连接占用 servlet 线程 → 高并发下连接/线程双瓶颈"
  location: backend/src/main/resources/application.yml:1
  evidence: |
    spring:
      datasource:
        url: jdbc:postgresql://...   # 无 hikari.maximum-pool-size / minimum-idle 等
    # 无 spring.task.execution.pool.* 配置；无 spring.mvc.async.request-timeout
    # SSE: STREAM_TIMEOUT_MS = 30min（DetectionEventSseService.java:29），每个看板客户端占一条 SSE 连接
  description: |
    连接池大小未配，HikariCP 默认 maximumPoolSize=10。叠加 PRF-02（投递在事务内持连接）+ PRF-03
    （外部调用无超时），10 条连接在突发投递下极易被长时间占满，阻塞所有读写请求。另外 SSE stream
    超时 30min，每个看板长连接占用一个 Tomcat 异步处理槽位；MVC async request-timeout 未设。园所/
    看板数增长时，连接池与异步线程容量都未按负载显式规划，属容量盲区。
  recommendation: |
    显式配置 spring.datasource.hikari.maximum-pool-size / minimum-idle（按 DB 与实例数算），
    spring.task.execution.pool.*（配合 PRF-01），spring.mvc.async.request-timeout。压测确认 SSE
    并发上限。这是把多个 high 项的「无界」收口为「可容量规划」的基础设施配置。
  confidence: medium
  cross_refs: [PRF-01, PRF-02, PRF-03]
```

```yaml
- id: PRF-08
  angle: performance
  component: backend
  severity: low
  title: "StaffAlertService 对每个收件人逐条 dispatch（各自独立短事务 + 各自一次 PushSubscription 查询）"
  location: backend/src/main/java/com/ai_kids_care/v1/service/StaffAlertService.java:71
  evidence: |
    for (Long userId : staffUserIds) {
        User recipient = userRepository.findById(userId).orElse(null);   // 每收件人一次查询
        deliver(... PUSH ...);   // deliver → notificationService.dispatch（各自 @Transactional）
        if (phone非空) deliver(... SMS ...);
    }
    // dispatch 内：pushSubscriptionRepository.findByUser_IdAndProviderAndStatus(...) 每次单查
  description: |
    告警对每个 staff：findById 一次 + dispatch 内 PushSubscription 查询一次 + 外部投递一次，逐人串行。
    staff 集合通常较小（单园 admin/teacher 数量级），故本身非高危；但与 PRF-02/PRF-03 叠加时，串行
    的逐人外部投递会把单事件告警的端到端延迟拉成 N×（网络往返）。GuardianNotificationService 已对
    用户做 findAllById 批量预载（loadUsers），StaffAlertService 未对齐——可批量预载 user 与
    subscription 减少往返。低量下可接受，记为改进项。
  recommendation: |
    批量预载 staff 的 User 与其 ACTIVE Pushover 订阅（findAllByUser_IdInAnd...），循环内只做投递；
    投递本身配合 PRF-02 移出事务后可并行化（受 PRF-01 有界池限流）。
  confidence: medium
  cross_refs: [PRF-01, PRF-02]
```

---

## 跨边界 / 需抄送
- **PRF-05（SSE 进程内注册表）+ PRF-04（调度无锁）** → 抄送 `integration-analyst`：多实例事件分发拓扑（Redis pub/sub 缺位）是集成边界问题；事件源 `DetectionIngestService.publishEvent` 是本地 ApplicationEvent，跨实例不传播。
- **PRF-02（事务内 IO）的结构根因** → 抄送 `architecture-analyst`：投递与持久化未分层（缺 outbox/投递 worker 边界），是同步阻塞结构性问题。
- **多实例去重 / 投递原子性** 缺对应测试 → 抄送 `quality-analyst`：无 multi-instance / 连接池耗尽 / 慢第三方超时的回归测试。

## 验证建议（交 finding-verifier，深度档 DooD）
- PRF-01：DooD 起容器，对 ingest 端点打并发，观测 JVM 线程数是否随事件数线性增长（确认 SimpleAsyncTaskExecutor 行为）。
- PRF-02/03：用慢 mock Pushover（sleep 10s）观测 HikariCP active connections 是否被占满、其他请求是否阻塞。
- PRF-06：开 `spring.jpa.show-sql` 或 Hibernate statistics，对 20 行列表 / replaySince 计实际 SELECT 次数，坐实 N+1 倍数。
