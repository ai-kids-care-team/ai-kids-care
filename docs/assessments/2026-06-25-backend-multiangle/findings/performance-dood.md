# Performance DooD 实跑坐实 — backend (PRF-01/02/03/06/07)

环境：develop worktree（dood-perf）。gradle:8.7-jdk21 容器内跑 testcontainers（真实 PG16 + initdb + V8 Flyway 迁移 + Redis），
探针类 `DetectionPerfProbeTest extends BaseIntegrationTest`（throwaway，未 commit）。
统计用 Hibernate `Statistics.getPrepareStatementCount()`（`hibernate.generate_statistics=true`）。
全部 6 个探针用例 BUILD SUCCESSFUL（两轮迭代后）。

---

## PRF-06 — DetectionEvent 列表 / SSE 重放 N+1（LAZY @ManyToOne 无 JOIN FETCH）

- **原静态判断**：medium，confidence=medium。预测每行额外 ~3 SELECT（kindergarten.name / cctvCameras.cameraName / rooms.name 三个 LAZY 标量），列表 20 行 → 1+~60；replay max=200 → ~600 次。
- **DooD 方法**：JdbcTemplate seed N 行 detection_events（KG=1）。**关键修正**：seed 里 KG=1 的关联目标基数极低（camera=1、rooms∈{1,2,3}、session=1），Hibernate L1 缓存使每个 LAZY 关联按「distinct 目标」只查一次 → 初版误测出 N=50 仅 7 条 SQL（假阴性，会误判 refuted）。改为**每行指向各自独立的 room**（JdbcTemplate 现建 N 个 distinct rooms），并在**冷持久化上下文**（每次 TransactionTemplate 新事务）内调用真实读路径 `repository.findByKindergarten_Id(...).map(mapper::toVO)` 与 replay 路径。
- **实测数字**（statements = 1 list + 关联载入）：
  | 读路径 | N | SQL 总条数 | 每行额外 SELECT |
  |---|---|---|---|
  | LIST | 50 | **54** | 1.06 |
  | LIST | 200 | **204** | 1.02 |
  | REPLAY(replaySince) | 200 | **203** | 1.01 |
- **解读**：放大曲线**确为线性**（N=50→54，N=200→204，斜率≈1）。本探针只让 `rooms` 维度高基数，故每行 +1；`kindergarten`(单园) 与 `cctvCameras`(单相机) 在本测里被共享、只各查一次。生产中每条事件的 camera/room/session 都可能不同 → 每行最坏 **+3**，与静态预测一致。replay max=200 实测 203 条 SQL，坐实「单次重连一个园即数百条 SELECT」。
- **升级 verdict**：**confirmed-measured**。N+1 真实存在、随行数线性放大；replay 路径 200 行 = 203 SQL 实测坐实。严重度：维持 **medium**（有 DB 重放兜底、非功能 bug；但重连风暴下 DB 压力实测成立，值得修：@EntityGraph / 投影 DTO）。

## PRF-01 — @EnableAsync 无自定义 Executor → 退化无界 SimpleAsyncTaskExecutor

- **原静态判断**：high，confidence=medium。grep 称「无 AsyncConfigurer / 无 ThreadPoolTaskExecutor @Bean」→ 推断 @Async 回退到无界 SimpleAsyncTaskExecutor（每任务新线程）。
- **DooD 方法**：启动真实上下文，枚举 `TaskExecutor`/`ThreadPoolTaskExecutor`/`AsyncConfigurer` bean，读 `applicationTaskExecutor` 线程池实际边界，并按 `AsyncExecutionAspectSupport.getDefaultExecutor` 逻辑解析 @Async 实际执行器。
- **实测数字**：
  - TaskExecutor beans = **[applicationTaskExecutor, taskScheduler]**（2 个）
  - ThreadPoolTaskExecutor beans = **[applicationTaskExecutor]**；AsyncConfigurer beans = **[]**
  - `applicationTaskExecutor` 实测边界：**corePoolSize=8, maxPoolSize=2147483647(Integer.MAX_VALUE), queue=LinkedBlockingQueue, remainingCapacity=2147483647**
  - 存在名为 **`taskExecutor`** 的 bean（= ThreadPoolTaskExecutor，Spring Boot 给 applicationTaskExecutor 注册的别名）。
- **解读（静态推断被部分反驳 + 仍有真问题）**：
  1. **反驳点**：项目**并非**退化到「每任务新线程」的 SimpleAsyncTaskExecutor。Spring Boot 自动配置了 `applicationTaskExecutor`（ThreadPoolTaskExecutor），且注册了别名 `taskExecutor` → `@Async` 解析 default executor 时按名 `taskExecutor` 命中这个**有 core=8 的线程池**，**不**回退 SimpleAsyncTaskExecutor。原 finding「无任何 Executor bean → SimpleAsyncTaskExecutor 每任务起新线程」的**机制描述不准确**。
  2. **仍成立的真问题**：该自动池 **maxPoolSize=Integer.MAX_VALUE 且 queue=无界 LinkedBlockingQueue（容量 Integer.MAX_VALUE）**。无界队列语义下 maxPoolSize 永不触发，突发任务**堆积在无界队列里**（内存压力 / 投递延迟无上界），无背压、无拒绝策略。即「无界」结论在**资源不设上限**这一核心层面**仍然成立**，只是失控形态从「线程数爆炸」变为「队列无界堆积 + core=8 串行消费导致投递积压」。
- **升级 verdict**：**confirmed-measured（机制修正）**。坐实「@Async 执行器未显式有界配置、队列无界无背压」；但**纠正**原文「退化到 SimpleAsyncTaskExecutor / 每任务新线程」的描述——实为 Boot 默认 ThreadPoolTaskExecutor（core=8 + 无界队列）。严重度：**high→建议降为 medium**（失控形态较温和：积压而非线程风暴；但仍无上界、无拒绝策略，需显式配置 spring.task.execution.pool.*）。

## PRF-03 — Pushover / Solapi 客户端无连接/读取超时

- **原静态判断**：high，confidence=high。两 bean 用 SDK 默认构造，未设 connect/read timeout。
- **DooD 方法**：取 `pushoverClient` / `solapiMessageService` bean，反射检视其内部 HTTP client 字段。
- **实测数字**：
  - `pushoverClient` = `net.pushover.client.PushoverRestClient`，内部 `httpClient = org.apache.http.impl.client.DefaultHttpClient`（Apache HC 默认构造，无应用层超时注入）。
  - `solapiMessageService` = `net.nurigo.sdk.message.service.DefaultMessageService`，内部 `messageHttpService = retrofit2.Retrofit`（默认 OkHttp/Retrofit，无应用层超时注入）。
  - 两处确认由 SDK 默认构造器创建，应用配置（PushoverClientConfig/SolapiClientConfig）**未注入任何 connect/read timeout**。
- **升级 verdict**：**confirmed-measured（运行时实证两 bean 类型 + 无超时注入路径）**。未实跑慢端点压测（需 mock 慢服务 + 连接池监控，超 30min 预算），故「拖垮连接池」的级联效应**保持静态**（unverified-dynamic）。严重度：维持 **high**（无超时是延迟热点根因，叠加 PRF-02 事务内 IO 放大）。

## PRF-07 — HikariCP 连接池默认 max 10

- **原静态判断**：medium，confidence=medium。application.yml 未配 pool size → 默认 maximumPoolSize=10。
- **DooD 方法**：取 `DataSource` bean，反射读 `getMaximumPoolSize()`。
- **实测数字**：DataSource = `com.zaxxer.hikari.HikariDataSource`，**maximumPoolSize = 10**（实测确认默认值）。
- **注意**：测试用 `@DynamicPropertySource` 只覆盖了 datasource.url，**未**覆盖 pool size，故此 10 即应用默认（application.yml 未配）的真实体现。
- **升级 verdict**：**confirmed-measured**。严重度：维持 **medium**（容量盲区；叠加 PRF-02/03 时 10 连接易被慢投递占满）。

## PRF-02 — 投递原子性（@Transactional 内外部 IO）

- **原静态判断**：high，confidence=high。代码注释自承 delivery-atomicity gap。
- **DooD 状态**：**未动态坐实（unverified-dynamic，保持静态 confirmed）**。注入点（让外部 push「成功」但其后 save 失败）需 mock PushoverService + 触发 SENT-save 异常，构造成本高、超 30min 预算上限。代码结构（dispatch 整方法 @Transactional + 中段外部 HTTP）静态清晰、且注释自认，静态判断稳固，不硬凑动态。

---

## 不做（诚实保持静态，需多实例环境人工验证）
- **PRF-04（DeferredNotificationScanner 无 ShedLock 多实例重复投递）**：单 JVM 单跑无法真测多实例竞态。静态成立（无 shedlock 依赖、类注释自承单实例假设）。**需双实例环境人工验证。**
- **PRF-05（SSE emitter 进程内 ConcurrentHashMap，多实例不跨进程）**：同上，单进程测不出跨实例丢事件。静态成立。**需双实例 + 跨实例 ingest 人工验证。**
- **PRF-08（low，逐收件人 dispatch）**：低危改进项，未投入 DooD。

## 覆盖与局限
- PRF-06/01/03/07 已运行时实测坐实（含对 PRF-01 机制描述与 PRF-06 测量方法的修正）。
- PRF-02/04/05 保持静态：原子性注入与多实例竞态非单 JVM 单跑可测，列入「需人工/多实例验证」。
- PRF-03 的「超时缺失」已实证，但「拖垮连接池的级联」未压测坐实（unverified-dynamic）。
