# Backend 多角度分析报告 — 兼「新分析团队」验证记分卡

> **状态**：2026-06-25 完成 · develop @ 57ad6e5（深度档 DooD 补测于 0767775）· 由 `component-analysis-orchestrator` 标准档 + performance 束深度档 DooD 产出。
> **本目录是什么**：组件多角度分析团队对 backend 的一次审查 + 对该团队自身的验证记分卡。所有 finding 经对抗式复核（15/15 confirmed），performance 束部分经 testcontainers 实测。
> **如何用（修真问题入口）**：本 README 是决策视图；逐条 finding 的 evidence/recommendation 看 `findings/{security,integration,performance}.md`，验证 verdict 看 `*.verified.md`，性能实测数字看 `findings/performance-dood.md`。**建议先修的 high**：PRF-02（投递原子性/outbox）、INT-01/02/03（前端未接线，用户影响最大）、PRF-03（外部客户端无超时）；多实例（PRF-04/05 ShedLock+Redis）为部署前置。
> **未覆盖**：architecture / quality / experience 三角度本跑未跑；如需整工程全景，对这三角度补跑标准档即可。

**运行**：2026-06-25 · 目标组件 backend（develop @ 57ad6e5）· 角度 security + integration + performance（首跑聚焦）
**档位**：标准档。**降级说明**：本环境无 `TeamCreate` 工具 → 按编排器错误处理自动降级为 **sub-agent fan-out + 对抗式验证**（仍跑 finding-verifier）。功能等价，差异仅在分析师间无实时 SendMessage 互证（由 lead 在综合期代为交叉）。
**方法**：盲测 fan-out（分析师禁读 `docs/assessments/` 与旧 `_workspace`，全部从代码独立推导）→ finding-verifier 对每条 high+medium 以「反驳为默认假设」静态复核 1 票 → lead 去重定级综合 + 对照独立真值打分。
**产出**：security 8 条 / integration 10 条 / performance 8 条 = 26 条；high+medium 15 条经复核 **15 confirmed / 0 refuted**。

---

## 一、验证记分卡（本次跑的首要目的）

真值来源：2026-06-17 follow-up 审计 + 项目 backlog 记忆。**关键修正**：审计后大量项已修复，真值需按「当前 develop 存活状态」重新校准——这本身是验证跑的产物。

### 召回（团队是否命中已知真问题）

| 真值项 | 我（lead）核实的 develop 现状 | 团队结果 | 判定 |
|---|---|---|---|
| **GT-2 dispatch 投递原子性**（外部 push/SMS 在 `@Transactional` 内） | **存活**：`NotificationService.java:134` `@Transactional` + :160 注释自承 | **PRF-02 (high, conf=high)** 精确命中，定位+自承注释 | ✅ 命中 |
| **GT-3 多实例去重缺失（ShedLock）** | **存活**：`DeferredNotificationScanner.java:20` 注释自承单实例假设、build.gradle 无依赖 | **PRF-04 (high)** 命中，grep 确证依赖缺失 | ✅ 命中 |
| **GT-1 TeacherService 漏 @PreAuthorize** | **存活但未接线**：`listTeachers` 用 `findAll` 无租户过滤、无注解，但**零 controller 引用**（潜在债，审计本身也列为「小 follow-up」） | **SEC-07 (info)** 捕获了这一整类（service-only 授权 + 列举未接线的 NotificationService CRUD + 建议 ArchUnit 守卫），但**未点名 TeacherService 实例** | ◐ 类命中、实例漏名；且其 info 定级比真值的隐含 high **更准** |
| **GT-4 API-001**（缺 @Valid / 全局 error envelope / stacktrace 暴露） | **多已修复**：`ApiExceptionHandler.java` 全局处理已存在、`application.yml:45 include-stacktrace: never` 已配、@Valid 覆盖 9/23 | 团队**未报** | ✅ 正确不报（真值过期；报了反成假阳性） |
| **dedup_key 契约**（AI↔ingest） | 命名两侧 camelCase 对齐、一致 | INT 主动**反证**「驼峰vs下划线错位不存在」，另报 INT-10 提交时刻而非真实 onset 的语义弱化 | ✅ 精度+深度俱佳 |

**召回小结**：存活真问题 GT-2 / GT-3 满命中，GT-1 类命中/实例漏名（且定级更准）。GT-4 与 dedup_key 经核实属「真值过期/伪命题」，团队正确规避。**对真正存活的硬伤 0 漏报。**

### 精度（是否制造假阳性）

| 精度陷阱（已修复/设计正确，误报=FP） | 团队表现 |
|---|---|
| RRN 是 HMAC 单向 | security 正面确认「HMAC + env pepper，无可逆残留」，**未误报可逆加密** ✅ |
| denyAll 空壳已封口 | security 确认 denyAll 占位正确 ✅ |
| internal 端点 CSRF 豁免 | security 判「token 鉴权非 cookie，justified」✅ |
| seed test pepper / 登录限流单实例 | 均判为「已知 follow-up，非本轮 finding」✅ |

**精度小结**：15 条 high+medium 经对抗式复核 **0 refuted**；4 个陷阱全避开，且 security/integration 主动写了 anti-false-positive 段。**精度≈100%，无 FP 存活。**

### 净增价值（真值清单之外、团队新发现的真问题）

团队不止复现已知，还**显著扩展**了已知边界（均经复核 confirmed）：

- **performance**：PRF-01 `@EnableAsync` 无自定义 Executor → 退化 `SimpleAsyncTaskExecutor` 无界起线程（high）；PRF-03 Pushover/Solapi 客户端无超时（high）；PRF-05 SSE emitter 进程内 map 多实例丢事件；PRF-06 DetectionEvent 4×LAZY `@ManyToOne` N+1（重连最坏 ~600 查询）；PRF-07 HikariCP 未显式配置。
- **security**：SEC-01 租户写操作（摄像头流/房间/班级/event-review）不落 SUCCESS 审计、`AuditAction` enum 无 CRUD 动作（medium）；SEC-02 AI 内部端点返回解密摄像头密码、无审计、共享静态 token 无轮换（medium）；SEC-03/04 注册可用性 & 监护人核验枚举 oracle；SEC-05 会话 cookie Secure 默认 false。
- **integration**：INT-01/02/03「后端就绪、前端未接线」三连（通知收件箱、Web Push 订阅、children/guardians/teachers 读 api 仍 stub）——**直接影响用户旅程，UX 影响 high**；INT-04/05 枚举漂移；INT-07 AI `start_time==end_time` 致事件时长恒为 0（纯 AI 端 bug）；INT-10 dedup 用提交时刻语义弱化。

**结论：团队在「复现已知真值」之外，新增了约 15 条经坐实的真问题，含多条 high。** 这是本次验证最强的信号——它不是在背审计答案，而是独立读码并比审计走得更远、定级更准。

---

## 二、确认 findings 综合（按归并束 + 全局定级）

> 仅列 confirmed。无 refuted（附录为空）。运行时严重度标注见「覆盖与局限」。

### 高优先（high）

1. **[束·投递原子性] PRF-02** — `NotificationService.dispatch()` 在 `@Transactional` 内做 Pushover/SMS 网络往返：持库连接 + 投递成功但 SENT save 失败→行卡 SENDING。被 StaffAlert/Guardian/Scanner 循环逐人调用放大。*建议*：投递移出事务（outbox/投递 worker），两阶段状态机。cross：architecture（缺 outbox 分层）。
2. **[束·资源无上界] PRF-01 + PRF-03 + PRF-07** — 异步执行器（无自定义 Executor→无界 SimpleAsync）、外部客户端（无超时）、连接池（Hikari 默认）三处均未配上界。突发检测流量下线程膨胀 + 无超时阻塞 + 连接耗尽叠加致命。*建议*：定义有界 `ThreadPoolTaskExecutor` + 拒绝策略；Pushover/Solapi 连接&读超时；显式 Hikari 池参数。
3. **[束·多实例缺位] PRF-04 + PRF-05** — `@Scheduled` 扫描器无 ShedLock（多实例重复推家长）+ SSE emitter 进程内 map（事件到不了别实例客户端）。*建议*：ShedLock/分布式锁 + Redis pub-sub 或粘性会话。cross：integration（事件分发拓扑）。
4. **[束·前端未接线] INT-01 + INT-02 + INT-03** — 通知收件箱 `GET /api/v1/notifications`、`push_subscriptions` CRUD、children/guardians/teachers 读端点后端均就绪+测试覆盖，**前端零消费者 / 显式 stub**。家长看不到告警历史、Web Push 投不到、核心列表拿不到真数据（stub 返 null 易误当空集）。*建议*：接线前端 api + SW 订阅；stub 改显式 Unavailable 而非静默 null。

### 中优先（medium）

5. **[束·审计盲区] SEC-01 + SEC-02** — 租户写操作与 AI 凭据读均不落审计、`AuditAction` 无 CRUD 动作；AI 凭据端点共享静态 token 无轮换。*建议*：写路径补 SUCCESS 审计（复用 SecurityAuditWriter）、接线 S1_EVIDENCE_READ、规划 token 轮换。
6. **PRF-06** — DetectionEvent 列表/SSE 重放 N+1（4 LAZY @ManyToOne 无 JOIN FETCH）。*建议*：JOIN FETCH 或 @EntityGraph。
7. **INT-04 / INT-05** — `status_enum` 前端 `CctvCameraStatus` 缺 REJECTED；initdb/dbml 基线落后迁移真值（文档失真）。*建议*：枚举单一 source-of-truth（cross：architecture）。
8. **INT-07** — AI `stream_live_alert_service.py:429` `start_time==end_time` → 事件时长恒 0，后端原样入库。*建议*：AI 端填真实 onset 窗口。

### 低/info（择要）
SEC-03/04 枚举 oracle（加限流+审计）、SEC-05 cookie Secure 默认 false（prod fail-closed）、SEC-06 CORS 硬编码缺 prod origin、SEC-08 rrn_first6 明文（设计内最小化，加测试守卫）、INT-10 dedup onset 语义、INT-06 compose 无 ai 服务、INT-09 SSE withCredentials 依赖 CORS。

---

## 三、角度健康评分（本跑 3/6 角度）

| 角度 | 评分 | 一句话 |
|---|---|---|
| security | **B+** | 认证/多租户/PII 核心模型扎实（每请求重解析、跨租户 404、RRN HMAC、注入免疫）；残留为审计覆盖盲区与若干无限流 oracle，无 critical/high。 |
| integration | **C+** | 边界 shape 一致性好（六条边界逐字段验证一致），但「后端就绪/前端未接线」系统性缺口大，多个用户旅程实际走不通。 |
| performance | **C** | 单实例可跑，但异步/外部IO/连接/调度/SSE 多处「未配上界 + 多实例未就绪」，一放大即坏；投递原子性是结构性硬伤。 |

> architecture / quality / experience 三角度本跑未覆盖；多条 finding 已标注 cross 到 architecture（outbox 分层、枚举 source-of-truth）。

---

## 三点五、深度档 DooD 实测结果（performance 束）

2026-06-25 对 performance 束追加深度档 DooD 实跑（testcontainers 真实 PG/Redis，gradle 容器内跑，全探针 BUILD SUCCESSFUL）。把「缺陷条件成立（静态）」升级为「实测数字」，并**证伪修正了一条**。

| finding | DooD 方法 | 实测 | 升级后 verdict / 严重度 |
|---|---|---|---|
| **PRF-06 N+1** | Hibernate Statistics 数 SQL | LIST N=50→**54**、N=200→**204**；REPLAY 200→**203**。探针只放大 room 一维（+1/row）；生产 camera/room/session 全异 → 最坏 **+3/row ≈ 600**（与静态预测吻合）。measured 204 是保守下界、~600 是 worst-case 上界 | **confirmed-measured** · medium（有 DB 重放兜底） |
| **PRF-07 Hikari** | 启动取 `HikariDataSource` | `maximumPoolSize=**10**`（默认未配） | **confirmed-measured** · medium |
| **PRF-03 无超时** | 运行时检视客户端 bean | `pushoverClient`(Apache DefaultHttpClient)、`solapiMessageService`(Retrofit) 均 SDK 默认无超时 | **confirmed-measured**（条件）；连接池级联未压测 → 维持 unverified-dynamic · high |
| **PRF-01 异步执行器** | 上下文实测 bean | **机制被修正**：实测存在 Boot 自动装配的 `applicationTaskExecutor`(ThreadPoolTaskExecutor，core=8 / maxPool=MAX / **无界 LinkedBlockingQueue**) → @Async **不**回退 SimpleAsyncTaskExecutor，原文「每任务新线程/线程爆炸」**不准**。真因=**无界队列堆积**（仍是无上界/无背压） | **confirmed-measured（机制修正）** · high→**medium** |
| PRF-02 原子性 | 注入失败构造成本高、超预算 | — | 维持静态 confirmed（注释自承稳固） |
| PRF-04 / PRF-05 多实例 | 单 JVM 测不出多实例竞态 | — | **维持静态**，需双实例环境人工验证 |

> DooD 净价值：①PRF-06 给出实测放大曲线（下界 204、worst-case ~600 两端都有据）；②**证伪并修正 PRF-01 的失效机制**（不是线程风暴，是无界队列堆积）——这是静态 grep 想当然「无自定义 Executor bean ⇒ SimpleAsyncTaskExecutor」漏掉了 Spring Boot 的 `TaskExecutionAutoConfiguration` 默认池所致。

---

## 四、覆盖与局限

1. **performance 束已部分 DooD 实测**（见三点五）：PRF-06/07/03/01 已坐实/修正；**PRF-02（原子性）、PRF-04/05（多实例竞态）仍未动态坐实**——前者注入成本高、后者需双实例环境，待人工或多实例压测确认。PRF-03 连接池级联耗尽时序未压测。
2. **角度部分覆盖**：仅 security/integration/performance；architecture/quality/experience 缺位。
3. **降级运行**：无 TeamCreate → fan-out，分析师间无实时互证（lead 综合期代为交叉）。

---

## 五、哈内斯改进建议（回灌 skill）

- **[高] gitignore 静态复核陷阱**：`frontend/` 在 repo `.gitignore` 内，Claude 的 Grep/Glob 默认**静默跳过**该树——会把「前端缺失」类 finding 误验成「文件不存在」的假坐实。本轮 finding-verifier 已用**裸 ripgrep 绕过**确证。应写进 `analyze-integration` 与 `adversarial-verification`：凡涉 .gitignore 内目录（前端/构建产物）的静态核查，必须绕过工具默认忽略。
- **[中] 真值会过期**：验证跑应在打分前由 lead 重新核实每条真值的「当前存活状态」（本轮 GT-4 已修复、GT-1 未接线均靠此校准），否则召回率会被陈旧清单污染。
- **[中] 标准档可补 TeamCreate 探测**：编排器启动即探测团队工具，缺失则提前声明降级，避免中途切换。
- **[高·新] 框架自动装配陷阱（performance 静态推断）**：「grep 无自定义 X bean ⇒ 退化到裸框架默认行为」是危险跳步。本轮 PRF-01 据此误判「@EnableAsync → SimpleAsyncTaskExecutor 线程爆炸」，DooD 实测却是 Spring Boot `TaskExecutionAutoConfiguration` 装配的 `applicationTaskExecutor`（有界线程 core=8 + 无界队列）。失效机制完全不同（队列堆积非线程风暴），严重度也变。应写进 `analyze-performance`：凡涉「缺某 bean → 默认行为」的推断，必须考虑 Boot starter 的 auto-configuration 覆盖，且把这类条目优先送 DooD 坐实机制、勿凭静态下严重度结论。
