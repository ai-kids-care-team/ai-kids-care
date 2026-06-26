---
name: analyze-performance
description: 从性能/可扩展性角度审查组件——N+1、事务内 IO、线程池、SSE/事件背压、多实例去重、缓存、延迟。performance-analyst 使用。当需要性能审查、扩展性评估、瓶颈/背压/多实例去重排查、延迟分析时使用。
---

# analyze-performance — 性能与可扩展性角度审查方法

从**运行时行为与扩展瓶颈**视角审查，回答「快不快、扛不扛得住扩展、多实例会不会出错」。每条结论落 `file:line`，按 `component-analysis-orchestrator` 的 finding schema 输出（id 前缀 `PRF-`）。

## 为何这样审（原则）
性能/扩展问题的代价是**规模放大后才爆发**——单实例低量时一切正常，多实例或高峰即重复发推、丢事件、线程爆炸、延迟飙升。所以重点找「现在能跑、放大即坏」的运行时行为，而非微观指令级优化（那是过早优化）。

## 检查清单（按本工程栈）

### N+1 / 数据访问
- JPA 关联 LAZY 在循环/`.stream().map()` 内被逐项触发（如 `AppreciationLetterService.buildVO`、`KindergartenAdminApprovalService.listPendingRegistrations`）。
- 缺 JOIN FETCH / 批量 `findAllByIdIn` 预载。

### 事务内外部 IO（重点）
- `@Transactional` 方法内做 Pushover/SMS/HTTP 调用（如 `NotificationService.dispatch`）——持库连接做网络往返 + 推成功但 DB 回滚的投递原子性缺口。
- 修向：事务外发 / outbox 模式。

### 线程模型
- `@EnableAsync` 的实际执行器：**别想当然**。裸 Spring 缺 Executor bean 才回退 `SimpleAsyncTaskExecutor`（每任务新线程）；但 **Spring Boot 的 `TaskExecutionAutoConfiguration` 默认装配 `applicationTaskExecutor`（有界线程 core=8 + 无界 `LinkedBlockingQueue`）**——所以 Boot 工程里失效机制通常是「**无界队列堆积/无背压**」而非线程爆炸。判前先确认是否 Boot starter、`spring.task.execution.*` 是否配了队列上限。（本工程 PRF-01 实测即此池。）
- `@Async` + AFTER_COMMIT 监听器内访问懒代理（LazyInitializationException / 懒代理 getPhone 坑）。

### 实时通道背压 / 多实例
- SSE：emitter 注册表是否进程内（`ConcurrentHashMap`）→ 多实例时 A 实例事件到不了 B 实例客户端；replay 上限、心跳间隔是否合理。
- `@Scheduled`（如 `DeferredNotificationScanner`）是否无分布式锁（ShedLock 缺失）→ 多实例重复执行、状态竞争。

### 缓存 / 上传 / 边缘
- 可缓存的重复查询是否每次打库。
- AI `/predict/upload` 是否整块 `await file.read()` 入内存再校验（并发 OOM 风险）。
- 边缘代理（Caddy `encode gzip`）是否缓冲 SSE 流 → 实时被延迟到心跳刷出。

## 手法
- Grep：`@Transactional`、`@Scheduled`、`@Async`、`@EnableAsync`、`for`/`stream().map` 内的 repository 调用、`SseEmitter`、`ConcurrentHashMap`。
- 读 `application.yml`、`docker-compose*.yml`、`Caddyfile` 理解拓扑与连接边界。
- 本机无 JVM/容器 → 静态推断标 confidence=medium；**深度档**把可实测项（延迟、线程、查询计数）交 `finding-verifier` 走 DooD（见 `adversarial-verification/references/dood-recipe.md`）坐实。
- ⚠️ **框架自动装配陷阱**：「grep 没有自定义 X bean ⇒ 退化到裸框架默认」是危险跳步——starter 的 auto-configuration 常已替你装配一个不同行为的默认（线程池、连接池、序列化器、错误处理器…）。凡此类「缺 bean → 默认行为」推断，**别凭静态下严重度/机制结论**，优先送 DooD 实测真实 bean（取 `applicationContext` 里的实际类型与参数）。机制错则严重度与修向都会错（PRF-01 实证）。

## 协作
跨边界性能问题 → SendMessage 抄送 `integration-analyst`；结构根因 → `architecture-analyst`；缺测试覆盖 → `quality-analyst`。完成写 `_workspace/performance_findings.md` 并通知 lead（附 top-3 瓶颈）。
